def call() {
    def dockerImage = null

    pipeline {
        agent { label 'master' }
        parameters { string(name: 'dockerStacksRepoCommitId',
                            defaultValue: '',
                            description: "ID коммита в репозитории ${Constants.dockerStacksGitRepoUrl}, " +
                                         "если оставить пустым, при деплое будет использован последний коммит в master")
                     booleanParam(name: 'skipToDeploy',
                                  defaultValue: false,
                                  description: 'пропустить сборку и тестирование')}
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image', 'Test Docker image structure', 'Push Docker image', 'Deploy service to swarm'])
        }
        stages {
            gitlabCommitStatus {
                stage('Build Docker image') {
                    when { not { expression { return params.skipToDeploy } } }
                    steps {
                        script { dockerImage = buildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: GIT_COMMIT[0..7] }
                    }
                }
            }
            gitlabCommitStatus {
                stage('Test Docker image structure') {
                    when {
                        allOf {
                            expression { fileExists 'container-structure-test.yaml' }
                            not { expression { return params.skipToDeploy } }
                        }
                    }
                    steps {
                        containerStructureTest image: dockerImage
                  }
                }
            }
            gitlabCommitStatus {
                stage('Push Docker image') {
                    when { not { expression { return params.skipToDeploy } } }
                    steps {
                        pushDocker image: dockerImage
                    }
                }
            }
            gitlabCommitStatus {
                stage('Pull Docker image') {
                    when { branch 'master' }
                    steps {
                        dockerPull image: dockerImage
                    }
                }
            }
            gitlabCommitStatus {
                stage('Deploy service to swarm') {
                    when { branch 'master' }
                    agent { label Constants.productionNodeLabel }
                    steps {
                        dockerStackDeploy stack: GROUP_NAME, service: PROJECT_NAME, image: dockerImage, dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
                    }
                    post {
                        success { notifySlack "${GROUP_NAME}/${PROJECT_NAME} deployed to production" }
                    }
                }
            }
        }
        post {
            success { cleanWs() }
            failure { notifySlack "Build failled: ${JOB_NAME} [<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}

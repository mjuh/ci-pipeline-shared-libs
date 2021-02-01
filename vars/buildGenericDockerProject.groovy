def call(Map args = [:]) {
    def dockerImage = null
    def slackMessages = []

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
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image', 'Test Docker image structure', 'Push Docker image'])
        }
        stages {
            stage('Build Docker image') {
                when {
                    not { expression { return params.skipToDeploy } }
                    beforeAgent true
                }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script { dockerImage = buildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: GIT_COMMIT[0..7] }
                    }
                }
            }
            stage('Test Docker image structure') {
                when {
                    allOf {
                        expression { fileExists 'container-structure-test.yaml' }
                        not { expression { return params.skipToDeploy } }
                    }
                }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        containerStructureTest image: dockerImage
                    }
                }
            }
            stage('Push Docker image') {
                when {
                    not { expression { return params.skipToDeploy } }
                    beforeAgent true
                }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        pushDocker image: dockerImage
                        script {
                            (args.postPush ?: { return true })([input: [image: dockerImage]])
                        }
                    }
                }
            }
            stage('Pull Docker image') {
                when {
                    allOf {
                        branch 'master'
                        not { expression { return args.skipDeploy }
                        }
                    }
                    beforeAgent true
                }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerPull image: dockerImage
                    }
                }
            }
            stage('Deploy service to swarm') {
                when {
                    allOf {
                        branch 'master'
                        not { expression { return args.skipDeploy }
                        }
                    }
                    beforeAgent true
                }
                agent { label Constants.productionNodeLabel }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerStackDeploy(
                            stack: (args.stack == null ? GROUP_NAME : args.stack),
                            service: PROJECT_NAME,
                            image: dockerImage,
                            dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
                        )
                    }
                }
                post {
                    success {
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                    }
                }
            }
        }
        post {
            always {
                sendSlackNotifications (
                    buildStatus: currentBuild.result,
                    threadMessages: slackMessages
                )
            }
            success { cleanWs() }
        }
    }
}

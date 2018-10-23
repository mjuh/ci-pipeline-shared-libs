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
                                  description: 'пропустить сборку и тестирование')
                     booleanParam(name: 'switchStacks',
                                  defaultValue: false,
                                  description: 'Свичнуть стэки?') 
                    }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
            INACTIVE_STACK = nginx.getInactive('/hms')
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Gradle', 'Build Docker image', 'Push Docker image'])
        }
       tools {
            gradle "latest"
        }
        stages {
            stage('Build Gradle') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                            sh ' gradle build ' 
                    }
                }
            }
            stage('Build Docker image') {
                when { not { expression { return params.skipToDeploy } } }
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
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        pushDocker image: dockerImage
                    }
                }
            }
            stage('Pull Docker image') {
                when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerPull image: dockerImage
                    }
                }
            }
            stage('Deploy service to swarm') {
                when { branch 'master' }
                agent { label Constants.productionNodeLabel }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerStackDeploy stack: INACTIVE_STACK, service: PROJECT_NAME, image: dockerImage, ports: Constants.hmsPorts."${INACTIVE_STACK}"."${PROJECT_NAME}", stackConfigFile: 'hms.yml', dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
                    }
                }
                post {
                    success {
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                    }
                }
            }
            stage('Switch stacks') {
                when {
                    allOf {
                        branch 'master'
                        expression { return params.switchStacks }
                    }
                }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                       script {nginx.Switch('/hms')}
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

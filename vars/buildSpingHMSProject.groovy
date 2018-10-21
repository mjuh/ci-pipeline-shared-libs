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
                     booleanParam(name: 'Switch',
                                  defaultValue: false,
                                  description: 'Свичнуть стэки?') 
                    }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Gradlew', 'Build Docker image', 'Test Docker image structure', 'Push Docker image'])
        }
        stages {
            stage('Build Gradlew') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                            sh ' ./gradlew build ' 
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
            stage('Detect InActive stack') {
                when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                            script {
                                nginx.check('/hms')
                                echo "${nginx.getInactive('/hms')}"
                            }
                    }
                }
            }
            stage('Switch stacks') {
                when {
                    allOf {
                        branch 'master'
                        expression { return Switch }
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

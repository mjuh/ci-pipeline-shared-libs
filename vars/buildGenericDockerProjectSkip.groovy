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
            buildDiscarder(logRotator(numToKeepStr: '3', artifactNumToKeepStr: '3'))
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image', 'Test Docker image structure', 'Push Docker image'])
        }
        stages {
            stage('Build Docker image') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script { dockerImage = buildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: GIT_COMMIT[0..7] }
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
                post {
                    success {
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME} builded and pushed"
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
def call(String composeProject) {
    def dockerImage = null

    pipeline {
        agent { label 'nixbld' }
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
        options { gitLabConnection(Constants.gitLabConnection) }
        stages {
            stage('Build Docker image') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script { dockerImage = nixBuildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: GIT_COMMIT[0..7] }
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
                        dockerPull image: dockerImage, nodeLabel: composeProject
                    }
                }
            }
            stage('Deploy service') {
                when { branch 'master' }
                agent { label composeProject }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerComposeDeploy project: composeProject, service: PROJECT_NAME, image: dockerImage, dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
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
            success { cleanWs() }
            failure { notifySlack "Build failled: ${JOB_NAME} [<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}

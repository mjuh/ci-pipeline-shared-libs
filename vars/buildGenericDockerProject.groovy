def call() {
    def dockerImage = null

    pipeline {
        agent { label 'master' }
        parameters { string(name: 'dockerStacksRepoCommitId', defaultValue: '', description: "ID коммита в репозитории ${Constants.dockerStacksGitRepoUrl}") }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options { gitLabConnection(Constants.gitLabConnection) }
        stages {
            stage('Build Docker image') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script { dockerImage = buildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: GIT_COMMIT[0..7] }
                    }
                }
            }
            stage('Test Docker image structure') {
                when { expression { fileExists 'container-structure-test.yaml' } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        containerStructureTest image: dockerImage
                    }
                }
            }
            stage('Push Docker image') {
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
                        dockerStackDeploy stack: GROUP_NAME, service: PROJECT_NAME, image: dockerImage, dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
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
def call() {
    def dockerImage = null

    pipeline {
        agent { label 'nixbld' }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image', 'Push Docker image'])
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        stages {
            stage('Build Docker image') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script { dockerImage = nixBuildDocker namespace: env.JOB_NAME, name: env.JOB_BASE_NAME }
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
        }
        post {
            success { cleanWs() }
            failure { notifySlack "Build failled: ${JOB_NAME} [<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}
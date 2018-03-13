def call() {
    def dockerImage = null

    pipeline {
        agent { label 'master' }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options { gitLabConnection(Constants.gitLabConnection) }
        stages {
            stage('Build Docker image') {
                steps {
                    notifyGitlab 'running'
                    script { dockerImage = buildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: BRANCH_NAME }
                }
                post {
                    success { notifyGitlab 'success' }
                    failure { notifyGitlab 'failure' }
                    aborted { notifyGitlab 'aborted' }
                }
            }
            stage('Test Docker image structure') {
                when { expression { fileExists 'container-structure-test.yaml' } }
                steps {
                    notifyGitlab 'running'
                    containerStructureTest image: dockerImage
                }
                post {
                    success { notifyGitlab 'success' }
                    failure { notifyGitlab 'failure' }
                    aborted { notifyGitlab 'aborted' }
                }
            }
            stage('Push Docker image') {
                steps {
                    notifyGitlab 'running'
                    pushDocker image: dockerImage
                }
                post {
                    success { notifyGitlab 'success' }
                    failure { notifyGitlab 'failure' }
                    aborted { notifyGitlab 'aborted' }
                }
            }
            stage('Deploy service to swarm') {
                when { branch 'master' }
                agent { label Constants.productionNodeLabel }
                steps {
                    notifyGitlab 'running'
                    dockerStackDeploy stack: GROUP_NAME, service: PROJECT_NAME
                }
                post {
                    success {
                        notifyGitlab 'success'
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                    }
                    failure { notifyGitlab 'failure' }
                    aborted { notifyGitlab 'aborted' }
                }
            }
        }
        post {
            success { cleanWs() }
            failure { notifySlack "Build failled: ${JOB_NAME} [<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}
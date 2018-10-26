def call(String dstpath) {

    pipeline {
        agent { label 'master' }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
            NODE_ENV = 'production'
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['npm operations', 'stash', 'unstash'])
        }
        stages {
            stage('npm operations') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        sh 'NODE_ENV=production npm install'
                        sh 'NODE_ENV=production npm run-script build'
                    }
                }
            }
            stage('stash') {
              when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dir('public') {
                            stash name: "my-stash", includes: "**"
                        }
                    }
                }
            }
            stage('unstash') {
              when { branch 'master' }
              steps {
                 gitlabCommitStatus(STAGE_NAME) {
                    node('dhost-production') {
                        dir(dstpath) {
                            unstash "my-stash"
                        }
                    }
                 }
              }
              post {
                 success {
                    notifySlack "${PROJECT_NAME} deployed"
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

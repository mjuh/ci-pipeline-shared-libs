def call(String dstpath) {

    pipeline {
        agent { label 'master' }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['npm operations', 'stash', 'unstash'])
        }
        stages {
            stage('npm operations') {
              when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        sh 'NODE_ENV=production npm install'
                        sh 'NODE_ENV=production npm run-script build'
                        sh 'NODE_ENV=production npm run-script build-test'
                    }
                }
            }
            stage('stash') {
              when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                       stash name: "my-stash", includes: "public/**"
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

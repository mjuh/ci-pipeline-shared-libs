def call(Map args = [:]) {
    String unstashHosts = args.unstashHosts ? args.unstashHosts : "dhost-production"
    pipeline {
        agent { label 'master' }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['npm install', 'artifacts'])
            preserveStashes(buildCount: 9)
        }
        stages {
            stage('npm install') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        sh 'npm --version'
                        sh 'npm install'
                    }
                }
            }
            stage('npm build') {
                when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        sh 'npm run-script build'
                    }
                }
            }
            stage('npm build ci') {
                when { not { branch 'master' }}
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        sh 'npm run-script build-test'
                    }
                }
            }
            stage('artifacts') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dir('public') {
                            stash name: "my-stash", includes: "**"
                            archiveArtifacts artifacts: "**"
                        }
                    }
                }
            }
            stage('unstash') {
              when { branch 'master' }
              steps {
                 gitlabCommitStatus(STAGE_NAME) {
                   script {
                       parallel (args.unstashHosts.collectEntries{ host ->
                                  [(host): {
                                          node(host) {
                                              dir(args.dstpath) {
                                                  unstash "my-stash"
                                              }
                                          }
                                  }]
                                })
                   }
                 }
              }
              post {
                 success {
                    notifySlack "${PROJECT_NAME} deployed to production"
                 }
              }
            }
            stage('unstash-ci') {
              when { not { branch 'master' }}
              steps {
                 gitlabCommitStatus(STAGE_NAME) {
                    node('ci') {
                        dir(args.dstpath) {
                            unstash "my-stash"
                        }
                    }
                 }
              }
              post {
                 success {
                    notifySlack "${PROJECT_NAME} deployed to ci"
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

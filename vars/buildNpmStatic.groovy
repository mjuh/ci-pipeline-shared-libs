def call(Map args = [:]) {
    String unstashHosts = args.unstashHosts ? args.unstashHosts : "dhost-production"
    pipeline {
        agent { label 'jenkins' }
        environment {
            PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GROUP_NAME = jenkinsJob.getGroup(env.JOB_NAME)
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            preserveStashes(buildCount: 9)
        }
        stages {
            stage('npm install') {
                steps {
                    script {
                        (args.preBuild ?: { return true })()
                    }
                    sh 'npm --version'
                    sh 'npm install'
                }
            }
            stage('npm build') {
                when { branch 'master'
                       beforeAgent true }
                steps {
                    script {
                        if (args.buildPhase) {
                            args.buildPhase(args)
                        } else {
                            sh 'npm run-script build'
                        }
                    }
                }
            }
            stage('npm build ci') {
                when { not { branch 'master' }
                       beforeAgent true }
                steps {
                    sh 'npm run-script build-test'
                }
            }
            stage('artifacts') {
                steps {
                    dir('public') {
                        stash name: "my-stash", includes: "**"
                        archiveArtifacts artifacts: "**"
                    }
                }
            }
            stage('unstash') {
              when { branch 'master'
                     beforeAgent true }
              steps {
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
                post {
                 success {
                    notifySlack "${PROJECT_NAME} deployed to production"
                 }
              }
            }
            stage('unstash-ci') {
              when { not { branch 'master' }}
              steps {
                    node('ci') {
                        dir(args.dstpath) {
                            unstash "my-stash"
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

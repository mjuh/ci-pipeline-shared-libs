// XXX: We cannot wait for "nix flake check" stage, because it could be
// skipped depending on when condition, so don't relly on "merge when
// succeeded" button in GitLab.

def quoteString(String string) {
    "'" + string + "'"
}

def call(Map args = [:]) {
    pipeline {
        agent { label "master" }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ["tests"])
        }
        stages {
            stage("tests") {
                steps {
                    script {
                        gitlabCommitStatus(STAGE_NAME) {
                            parallel (nix.check(args))
                        }
                    }
                }
            }
            stage("deploy") {
                when {
                    allOf {
                        branch "master"
                        expression { args.deploy == true }
                    }
                    beforeAgent true
                }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        ansiColor("xterm") {
                            sh ((["nix-shell --run",
                                  quoteString ((["deploy", ".", "--"]
                                                + Constants.nixFlags
                                                + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" "))
                        }
                    }
                }
            }
        }
        post {
            always {
                sendNotifications currentBuild.result
            }
        }
    }
}

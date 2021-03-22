def quoteString(String string) {
    "'" + string + "'"
}

def call(Map args = [:]) {
    pipeline {
        agent { label "master" }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ["No credentials", "nix flake check"])
        }
        stages {
            stage("Tests") {
                parallel {
                    stage("No credentials") {
                        steps {
                            gitlabCommitStatus(STAGE_NAME) {
                                build (
                                    job: "../../ci/bfg/master",
                                    parameters: [string(
                                            name: "GIT_REPOSITORY_TARGET_URL",
                                            value: gitRemoteOrigin.getRemote().url
                                        )
                                    ]
                                )
                            }
                        }
                    }
                    stage("nix flake check") {
                        when {
                            anyOf {
                                expression { args.deploy != true }
                                not { branch "master" }
                            }
                        }
                        steps {
                            gitlabCommitStatus(STAGE_NAME) {
                                ansiColor("xterm") {
                                    sh (nix.shell (run: ((["nix flake check"]
                                                          + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                          + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))
                                }
                            }
                        }
                    }
                }
            }
            stage("Deploy") {
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
                                  quoteString ((["deploy", "--"]
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

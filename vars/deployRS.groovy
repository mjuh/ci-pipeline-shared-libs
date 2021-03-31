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
            disableConcurrentBuilds()
	}
        stages {
            stage("tests") {
                steps {
                    script {
                        gitlabCommitStatus(STAGE_NAME) {
                            parallel ([:]
                                      + (args.scanPasswords == true ?
                                         ["bfg": {
                                            build (job: "../../ci/bfg/master",
                                                   parameters: [string(name: "GIT_REPOSITORY_TARGET_URL",
                                                                       value: gitRemoteOrigin.getRemote().url)])}]
                                         : [:])
                                      + (args.deploy != true || GIT_BRANCH != "master" ?
                                         ["nix flake check": {
                                            ansiColor("xterm") {
                                                sh (nix.shell (run: ((["nix flake check"]
                                                                      + Constants.nixFlags
                                                                      + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                                      + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))}}]
                                         : [:]))
                        }
                    }
                }
            }
            stage("dry-activate") {
                when {
		    not {
                        branch "master"
		    }
		}
		steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        ansiColor("xterm") {
                            sh ((["nix-shell --run",
                                  quoteString ((["deploy", "--dry-activate", "true", ".", "--"]
                                                + Constants.nixFlags
                                                + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" "))
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

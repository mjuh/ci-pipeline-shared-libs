// XXX: We cannot wait for "nix flake check" stage, because it could be
// skipped depending on when condition, so don't relly on "merge when
// succeeded" button in GitLab.

def quoteString(String string) {
    "'" + string + "'"
}

def gc(Boolean enable) {
    enable == false ? "1" : "0"
}

@NonCPS
def hostsInChangeSets() {
    output = []
    currentBuild.changeSets.each { changeSet ->
        changeSet.items.each { entry ->
            (new ArrayList(entry.affectedFiles)).each { file ->
                if (file.path.startsWith("hosts")) {
                    output = output + file.path.split("/").last() - ".nix"
                }
            }
        }
    }
    output
}

def call(Map args = [:]) {
    pipeline {
        agent { label "master" }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ["tests"])
            disableConcurrentBuilds()
	}
        environment {
            GC_DONT_GC = gc(args.gc)
        }
        stages {
            stage("tests") {
                steps {
                    script {
                        if (args.checkPhase) {
                            gitlabCommitStatus(STAGE_NAME) {
                                ansiColor("xterm") {
                                    args.checkPhase(args)
                                }
                            }
                        } else {
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
                                // WARNING: Try to dry activate only after BFG
                                // succeeded to check no credentials are leaked.
                                ansiColor("xterm") {
                                    sh ((["nix-shell --run",
                                          quoteString ((["deploy", "--skip-checks", "--dry-activate", ".", "--"]
                                                        + Constants.nixFlags
                                                        + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                        + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" "))
                                }
                            }
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
                    script {
                        gitlabCommitStatus(STAGE_NAME) {
                            ansiColor("xterm") {
                                if (args.deployPhase) {
                                    args.deployPhase(args)
                                } else {
                                    if (args.sequential) {
                                        // Hosts in changeSet are first.
                                        hosts = (hostsInChangeSets() + findFiles(glob: 'hosts/*.nix').collect { file -> host = file.split("/").last() - ".nix"; "${host}"}).unique()

                                        counter = 0
                                        hosts.each{ host ->
                                            counter += 1
                                            echo("${counter}/${hosts.size()}")
                                            sh ((["nix-shell --run",
                                                  quoteString ((["deploy", "--skip-checks", "--debug-logs", ".#${host}", "--"]
                                                                + Constants.nixFlags
                                                                + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                                + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" ")) 
                                        }
                                    } else {
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

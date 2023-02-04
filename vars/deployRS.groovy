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
                if (file.path.startsWith("hosts") && file.path.endsWith(".nix")) {
                    output = output + (file.path.split("/").last() - ".nix")
                }
            }
        }
    }
    output
}

def applyToHostsSequentially(Closure closure, List<String> hosts) {
    counter = 0
    hosts.each{ host ->
        counter += 1
        echo("${counter}/${hosts.size()}")
        closure(host)
    }
}

def call(Map args = [:]) {
    pipeline {
        agent { label "jenkins" }
        options {
            disableConcurrentBuilds()
            timeout(time: 3, unit: "HOURS")
            ansiColor('xterm')
	}
        environment {
            GC_DONT_GC = gc(args.gc)
        }
        stages {
            stage("update") {
                steps {
                    script {
                        (args.preBuild ?: { return true })()
                        nixFlakeLockUpdate (inputs: ["ssl-certificates"])
                        (args.postBuild ?: { return true })()
                    }
                }
            }
            stage("tests") {
                steps {
                    script {
                        def (GROUP_NAME, PROJECT_NAME) =
                            (env.GIT_URL.split(":")[1].split("/")).collect({ string -> string.contains(".git") ? string - ".git" : string })

                        // Hosts in changeSet are first.
                        hosts = (hostsInChangeSets().findAll{ fileExists("hosts/${it}.nix") } + findFiles(glob: 'hosts/*.nix').collect { file -> "${file}".split("/").last() - ".nix" }).unique()

                        if (args.checkPhase) {
                            args.checkPhase(args)
                        } else {
                            parallel ([:]
                                      + (args.scanPasswords == true ?
                                         ["bfg": {
                                            build (job: "../../${Constants.bfgJobName}/master",
                                                   parameters: [
                                                    string(name: "GIT_REPOSITORY_TARGET_URL",
                                                           value: env.GIT_URL),
                                                    string(name: "PROJECT_NAME",
                                                           value: PROJECT_NAME),
                                                    string(name: "GROUP_NAME",
                                                           value: GROUP_NAME),
                                                ])}]
                                         : [:])
                                      + (args.deploy != true || GIT_BRANCH != "master" ?
                                         ["nix flake check": {
                                           sh (nix.shell (run: "nix flake show"))
                                           sh (nix.shell (run: ((["nix flake check"]
                                                                 + Constants.nixFlags
                                                                 + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                                 + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))
                                    }]
                                         : [:]))
                            // WARNING: Try to dry activate only after BFG
                            // succeeded to check no credentials are leaked.
                            sh ((["nix-shell --run",
                                  quoteString ((["deploy", "--skip-checks", "--dry-activate"]
                                                + (args.deployRsOptions == null ? [] : args.deployRsOptions)
                                                + [".", "--"]
                                                + Constants.nixFlags
                                                + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" "))
                        }
                        (args.postTests ?: { return true })()
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
                        if (args.deployPhase == null) {
                            if (args.sequential) {
                                applyToHostsSequentially({ host ->
                                    (args.preHostDeploy ?: { return true })([host: host])
                                    sh ((["nix-shell --run",
                                          quoteString ((["deploy", "--skip-checks", "--debug-logs"]
                                                        + (args.deployRsOptions == null ? [] : args.deployRsOptions)
                                                        + (args.flake == null ? ".#\\\"${host}\\\"." : args.flake)
                                                        + ["--"]
                                                        + Constants.nixFlags
                                                        + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                        + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" "))
                                    (args.postHostDeploy ?: { return true })([host: host])
                                },
                                    hosts)
                            } else {
                                sh ((["nix-shell --run",
                                      quoteString ((["deploy"]
                                                    + (args.deployRsOptions == null ? [] : args.deployRsOptions)
                                                    + (args.flake == null ? "." : args.flake)
                                                    + ["--"]
                                                    + Constants.nixFlags
                                                    + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                    + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))]).join(" "))
                            }
                            nix.commitAndPushFlakeLock()
                        }
                        else {
                            args.deployPhase(args)
                        }
                        (args.postDeploy ?: { return true })()
                    }
                    publishers {
                        findText regexp: 'ERROR', alsoCheckConsoleOutput: true
                    }
                }
            }
        }
        post {
            always {
                script {
                    (args.always ?: { return true })()
                }
            }
        }
    }
}

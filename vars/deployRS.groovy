def call(Map args = [:]) {
    pipeline {
        agent { label "master" }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ["Tests"])
        }
        stages {
            stage("Tests") {
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
                        sh (nix.shell (run: ((["nix flake check"]
                                              + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                              + (args.showTrace == true) ? ["--show-trace"] : []).join(" "))))
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

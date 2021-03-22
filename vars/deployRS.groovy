def call(Map args = [:]) {
    pipeline {
        agent { label "master" }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ["Tests"])
        }
    	parameters {
            booleanParam(
                name: "deploy",
                defaultValue: false,
                description: "Задеплоить?"
            )
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
            stage("Deploy") {
                when {
                    allOf {
                        branch "master"
                        expression { return (args.deploy || params.deploy) }
                    }
                    beforeAgent true
                }
                steps {
                    sh (nix.shell (run: ((["deploy", "--"]
                                          + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                          + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))
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

def call(Map args = [:]) {
    def slackMessages = []

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
                        sh (nix.shell (run: "nix flake check --print-build-logs --show-trace"))
                    }
                }
            }
	    stage("Deploy") {
		steps {
		    gitlabCommitStatus(STAGE_NAME) {
			sh(nix.shell(run: "deploy . -- --print-build-logs --show-trace"))
			slackMessages += "Deploy-rs: deployed ${deployment} to production"
		    }
		}
	    }
        }
        post {
            sendSlackNotifications (
                buildStatus: currentBuild.result,
                threadMessages: slackMessages
            )
        }
    }
}

def call(String composeProject, Map args = [:]) {
    def dockerImage = null

    pipeline {
        agent { label 'nixbld' }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
	    GITLAB_PROJECT_PATH_NAMESPACE = "${GITLAB_PROJECT_NAMESPACE}/${GITLAB_PROJECT_NAME}"
            DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GITLAB_PROJECT_PATH_NAMESPACE}/tag/${TAG}"
        }
        options {
            timeout(time: 2, unit: "HOURS")
	}
        stages {
            stage("build") {
                agent { label "jenkins" }
                steps {
                    script {
                        (args.preBuild ?: { return true })()

                        nixFlakeLockUpdate (inputs: ["ssl-certificates"])

                        if (args.buildPhase) {
                            ansiColor("xterm") {
                                args.buildPhase(args)
                            }
                        } else {
                            sh (nix.shell (run: ((["nix", "build"]
                                                  + Constants.nixFlags
                                                  + ["--out-link", "result/${env.JOB_NAME}/docker-${env.BUILD_NUMBER}", ".#container"]
                                                  + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                        }
                    }
                }
            }
            stage('Deploy service') {
                when {
                    branch 'master'
                    beforeAgent true
                }
                agent { label "jenkins" }
                steps {
                    script {
                        lock("docker-registry") {
                            sh (nix.shell (run: ((["nix", "run"]
                                                  + Constants.nixFlags
                                                  + [".#deploy"]
                                                  + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))

                            dockerImage = new DockerImageTarball(
                                imageName: (Constants.dockerRegistryHost + "/" + GITLAB_PROJECT_NAMESPACE + "/" + GITLAB_PROJECT_NAME + ":" + gitCommit().take(8)),
                                path: "" // XXX: Specifiy path in DockerImageTarball for flake buildWebService.
                            )

                            // Deploy with docker-compose
                            if (GIT_BRANCH == "master") {
                                if (composeProject) {
                                    sequentialCall (
                                        nodeLabels: [composeProject],
                                        procedure: { nodeLabels ->
                                            ansiColor("xterm") {
                                                dockerComposeDeploy (
                                                    project: composeProject,
                                                    service: GITLAB_PROJECT_NAME,
                                                    image: dockerImage,
                                                    dockerStacksRepoCommitId: params.dockerStacksRepoCommitId,
                                                    projectConfigFile: composeProject + "-" + env.NODE_NAME + ".yml"
                                                )
                                            }
                                        }
                                    )
                                }
                                nix.commitAndPushFlakeLock()
                            }

                            (args.postDeploy ?: { return true })([input: [
                                image: dockerImage,
                                PROJECT_NAME: GITLAB_PROJECT_NAME]])
                        }
                    }
                }
            }
        }
    }
}

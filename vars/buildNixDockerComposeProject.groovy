def call(String composeProject, Map args = [:]) {
    def dockerImage = null

    pipeline {
        agent { label 'nixbld' }
        parameters { string(name: 'dockerStacksRepoCommitId',
                defaultValue: '',
                description: "ID коммита в репозитории ${Constants.dockerStacksGitRepoUrl}, " +
                        "если оставить пустым, при деплое будет использован последний коммит в master")
            booleanParam(name: 'skipToDeploy',
                    defaultValue: false,
                    description: 'пропустить сборку и тестирование')}
        environment {
            PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GROUP_NAME = jenkinsJob.getGroup(env.JOB_NAME)
        }
        options {
            timeout(time: 2, unit: "HOURS")
	}
        stages {
            stage("build") {
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
                agent { label composeProject }
                steps {
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
                            if (args.stackDeploy) {
                                if (args.dockerStackServices == null) {
                                    dockerStackServices = [ GITLAB_PROJECT_NAME ] + (args.extraDockerStackServices == null ? [] : args.extraDockerStackServices)
                                } else {
                                    dockerStackServices = args.dockerStackServices
                                }
                                node(Constants.productionNodeLabel) {
                                    (args.services == null ? [ PROJECT_NAME ] : args.services).each { service ->
                                        dockerComposeDeploy (
                                            project: composeProject,
                                            service: service,
                                            image: dockerImage,
                                            dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
                                        )
                                    }
                                }
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

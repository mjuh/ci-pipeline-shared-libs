def call(def Map args = [:]) {
    String GRADLE_OPTS = args.GRADLE_OPTS == null ? "" : args.GRADLE_OPTS.join(' ')
    pipeline {
        agent { label "jenkins" }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
            INACTIVE_STACK = nginx.getInactive("/hms")
            GRADLE_OPTS = "${GRADLE_OPTS}"
            GRADLE_USER_HOME = "/var/lib/jenkins"
        }
        parameters {
            booleanParam(name: "switchStacks",
                         defaultValue: false,
                         description: "Свичнуть стэки?")
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: "10", artifactNumToKeepStr: "10"))
        }
        tools {
            gradle (args.gradle ?: "4")
            jdk (args.java ?: "8")
        }
        stages {
            stage("build-nix-sping-hms-project") {
                when {
                    allOf {
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                steps {
                    script {
                        (args.preBuild ?: { return true })()
                        sh "java -version; gradle -version; gradle build"
                        (args.postBuild ?: { return true })()
                        sh (nix.shell(run: (nix.build())))
                        sh (nix.shell(run: ((["nix", "run"]
                                             + Constants.nixFlags
                                             + [".#deploy"]
                                             + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                        dockerStackServices = [ GITLAB_PROJECT_NAME ] + (args.extraDockerStackServices == null ? [] : args.extraDockerStackServices)
                    }
                }
            }
            stage("Deploy service to swarm") {
                when {
                    allOf {
                        branch "master"
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                agent { label Constants.productionNodeLabel }
                steps {
                    script {
                        dockerImage = new DockerImageTarball(
                            imageName: (Constants.dockerRegistryHost + "/" + GITLAB_PROJECT_NAMESPACE + "/" + GITLAB_PROJECT_NAME + ":" + gitTag()),
                            path: "" // XXX: Specifiy path in DockerImageTarball for flake buildWebService.
                        )
                        node(Constants.productionNodeLabel) {
                            dockerStackServices.each { service ->
                                slackMessages += dockerStackDeploy (
                                    stack: GITLAB_PROJECT_NAMESPACE,
                                    service: service,
                                    image: dockerImage
                                )
                            }
                        }
                    }
                }
            }
        }
        post {
            always {
                archiveArtifacts (artifacts: 'build/reports/**',
                                  allowEmptyArchive: true,
                                  followSymlinks: true,
                                  onlyIfSuccessful: false)
            }
        }
    }
}

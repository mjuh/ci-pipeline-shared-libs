def call(def Map args = [:]) {
    def dockerImage = null
    def slackMessages = [];
    String GRADLE_OPTS = args.GRADLE_OPTS == null ? "" : args.GRADLE_OPTS.join(' ')

    pipeline {
        agent { label "jenkins" }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
            GRADLE_OPTS = "${GRADLE_OPTS}"
            GRADLE_USER_HOME = "/var/lib/jenkins"
        }
        parameters {
            string(
                name: "dockerStacksRepoCommitId",
                defaultValue: "",
                description: "ID коммита в репозитории ${Constants.dockerStacksGitRepoUrl}, " +
                    "если оставить пустым, при деплое будет использован последний коммит в master"
            )
            booleanParam(
                name: "skipToDeploy",
                defaultValue: false,
                description: "пропустить сборку и тестирование"
            )
            booleanParam(
                name: "switchStacks",
                defaultValue: false,
                description: "Свичнуть стэки?"
            )
        }
        options {
            buildDiscarder(
                logRotator(
                    numToKeepStr: "10",
                    artifactNumToKeepStr: "10"
                )
            )
        }
        tools {
            gradle (args.gradle ?: "4")
            jdk (args.java ?: "8")
        }
        stages {
            stage("Build Gradle") {
                when {
                    allOf {
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                steps {
                    script { (args.preBuild ?: { return true })() }
                    sh "java -version; gradle -version"
                    sh ((["gradle", "build"] + (args.gradleOptions == null ? [] : args.gradleOptions)).join(" "))
                }
            }
            stage("Build Docker jdk image") {
                when {
                    allOf {
                        expression { fileExists "Dockerfile.jdk" }
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                }
                steps {
                    script {
                        dockerImage = buildDocker (
                            namespace: GITLAB_PROJECT_NAMESPACE,
                            dockerfile: "Dockerfile.jdk",
                            name: GITLAB_PROJECT_NAME,
                            tag: gitHeadShort() + "-jdk"
                        )
                    }
                }
            }
            stage("Push Docker jdk image") {
                when {
                    allOf {
                        expression { fileExists "Dockerfile.jdk" }
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                }
                steps {
                    pushDocker image: dockerImage
                }
            }
            stage("Build Docker jre image") {
                when {
                    allOf {
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                steps {
                    script {
                        tag = gitHeadShort()
                        dockerImage = buildDocker (
                            namespace: GITLAB_PROJECT_NAMESPACE,
                            name: GITLAB_PROJECT_NAME,
                            tag: tag
                        )
                    }
                }
            }
            stage("Test Docker image structure") {
                when {
                    allOf {
                        expression { fileExists "container-structure-test.yaml" }
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                }
                steps {
                    containerStructureTest image: dockerImage
                }
            }
            stage("Push Docker image") {
                when {
                    allOf {
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                steps {
                    script {
                        pushDocker image: dockerImage
                        String DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GITLAB_PROJECT_NAMESPACE}/${GITLAB_PROJECT_NAME}/tag/${tag}"
                        slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"
                    }
                }
            }
            stage("Pull Docker image") {
                when {
                    allOf {
                        branch "master"
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                steps {
                    dockerPull image: dockerImage
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
                    dockerStackDeploy (
                        stack: nginx.Inactive("/hms"),
                        service: GITLAB_PROJECT_NAME,
                        image: dockerImage,
                        stackConfigFile: "hms.yml",
                        dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
                    )
                }
            }
            stage("Switch stacks") {
                when {
                    allOf {
                        branch "master"
                        expression { params.switchStacks }
                    }
                    beforeAgent true
                }
                steps {
                    script {
                        nginx.Switch("/hms")
                    }
                }
            }
        }
        post {
            always {
                sendSlackNotifications (
                    buildStatus: currentBuild.result,
                    threadMessages: slackMessages
                )
                archiveArtifacts (
                    artifacts: 'build/reports/**',
                    allowEmptyArchive: true,
                    followSymlinks: true,
                    onlyIfSuccessful: false
                )
            }
        }
    }
}

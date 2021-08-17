def call(def Map args = [:]) {
    def dockerImage = null
    def slackMessages = [];
    String GRADLE_OPTS = args.GRADLE_OPTS == null ? "" : args.GRADLE_OPTS.join(' ')

    pipeline {
        agent { label "master" }
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
        environment {
            INACTIVE_STACK = nginx.getInactive("/hms")
            GRADLE_OPTS = "${GRADLE_OPTS}"
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
            gradle (args.gradle ?: "latest")
        }
        stages {
            stage("Build Gradle") {
                when {
                    allOf {
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                }
                steps {
                    runGradle(java: args.java)
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
                            tag: GIT_COMMIT[0..7] + "-jdk"
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
                }
                steps {
                    script {
                        dockerImage = buildDocker (
                            namespace: GITLAB_PROJECT_NAMESPACE,
                            name: GITLAB_PROJECT_NAME,
                            tag: GIT_COMMIT[0..7]
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
                }
                steps {
                    script {
                        pushDocker image: dockerImage
                        String DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GITLAB_PROJECT_PATH_NAMESPACE}/tag/${GIT_COMMIT[0..7]}"
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
                }
                agent { label Constants.productionNodeLabel }
                steps {
                    dockerStackDeploy (
                        stack: INACTIVE_STACK,
                        service: GITLAB_PROJECT_NAME,
                        image: dockerImage,
                        stackConfigFile: "hms.yml",
                        dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
                    )
                }
                post {
                    success {
                        notifySlack "${GITLAB_PROJECT_PATH_NAMESPACE} deployed to ${INACTIVE_STACK} stack"
                    }
                }
            }
            stage("Switch stacks") {
                when {
                    allOf {
                        branch "master"
                        expression { params.switchStacks }
                    }
                }
                steps {
                    script {
                        nginx.Switch("/hms")
                        slackMessages +=
                            "Switched to ${INACTIVE_STACK} stack"
                    }
                }
            }
        }
        post {
            success { cleanWs() }
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

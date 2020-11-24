def call(Map args = [:]) {
    def slackMessages = []
    pipeline {
        agent { label "nixbld" }
        options {
            gitLabConnection(library("mj-shared-library").Constants.gitLabConnection)
            gitlabBuilds(builds: ["Build Docker image", "Test", "Push Docker image"])
            buildDiscarder(logRotator(numToKeepStr: "10", artifactNumToKeepStr: "10"))
        }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        parameters {
            booleanParam(
                name: "skipToDeploy",
                defaultValue: false,
                description: "пропустить сборку и тестирование"
            )
        }
        stages {
            stage("Build Docker image") {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            dockerImage = nixBuildDocker (
                                namespace: GROUP_NAME, 
                                name: PROJECT_NAME, 
                                currentProjectBranch: GIT_BRANCH,
                                overlaybranch: "master"
                            )
                        }
                    }
                }
            }
            stage("Test") {
                when { not { expression { params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        build (
                            job: "../mail-tests/master",
                            parameters: [
                                string(name: "TARGET_PROJECT", value: PROJECT_NAME),
                                string(name: "TARGET_BRANCH", value: GIT_BRANCH)
                            ]
                        )
                    }
                }
            }
            stage("Push Docker image") {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            pushDocker (
                                image: dockerImage,
                                pushToBranchName: false
                            )
                            slackMessages += "${GROUP_NAME}/${PROJECT_NAME}:${GIT_BRANCH} pushed to registry"
                        }
                    }
                }
            }
            stage("Deploy") {
                when { branch "master" }
                steps {
                    script {
                        (args.deploy ?: { return false })([input: [
                                    image: dockerImage,
                                    PROJECT_NAME: PROJECT_NAME]])
                    }
                }
            } 
        } 
        post {
            success { cleanWs() }
            always {
                sendSlackNotifications (buildStatus: currentBuild.result,
                                        threadMessages: slackMessages)
            }
        }
    }
}

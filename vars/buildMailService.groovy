def call(Map args = [:]) {
    def slackMessages = []
    pipeline {
        agent { label "nixbld" }
        options {
            buildDiscarder(logRotator(numToKeepStr: "10", artifactNumToKeepStr: "10"))
        }
        environment {
            PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GROUP_NAME = jenkinsJob.getGroup(env.JOB_NAME)
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
            stage("Test") {
                when {
                    not { expression { params.skipToDeploy } }
                    beforeAgent true
                }
                steps {
                    build (
                        job: "../mail%252Fmail-tests/master",
                        parameters: [
                            string(name: "TARGET_PROJECT", value: PROJECT_NAME),
                            string(name: "TARGET_BRANCH", value: GIT_BRANCH)
                        ]
                    )
                }
            }
            stage("Push Docker image") {
                steps {
                    script {
                        pushDocker (
                            image: dockerImage,
                            pushToBranchName: false
                        )
                        slackMessages += "${GROUP_NAME}/${PROJECT_NAME}:${GIT_BRANCH} pushed to registry"
                    }
                }
            }
            stage("Deploy") {
                when {
                    branch "master"
                    beforeAgent true
                }
                steps {
                    script {
                        (args.deploy ?: { return false })([input: [
                                    image: dockerImage,
                                    PROJECT_NAME: PROJECT_NAME]])
                    }
                }
            } 
        } 
    }
}

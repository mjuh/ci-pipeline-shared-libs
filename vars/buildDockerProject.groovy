def call() {
    pipeline {
        agent { label "jenkins" }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
        }
        options {
            buildDiscarder(
                logRotator(
                    numToKeepStr: "10",
                    artifactNumToKeepStr: "10"
                )
            )
        }
        stages {
            stage("build") {
                when {
                    allOf {
                        branch "master"
                        expression { fileExists "Dockerfile" }
                    }
                }
                steps {
                    script {
                        dockerImage = buildDocker (
                            namespace: GITLAB_PROJECT_NAMESPACE,
                            dockerfile: "Dockerfile",
                            name: GITLAB_PROJECT_NAME,
                            tag: gitHeadShort()
                        )
                    }
                }
            }
            stage("push") {
                when {
                    allOf {
                        branch "master"
                        expression { fileExists "Dockerfile" }
                    }
                }
                steps {
                    pushDocker image: dockerImage
                }
            }
        }
    }
}

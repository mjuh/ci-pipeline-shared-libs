def call(String phpVersion) {
    def dockerImage = null

    pipeline {
        agent { label 'master' }
        options {
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
        }
        stages {
            stage('Install PHP dependencies with Composer') {
                steps {
                    println "id".execute().text
                    script {
                        echo "GITLAB_PROJECT_NAMESPACE=${GITLAB_PROJECT_NAMESPACE}"
                        echo "GITLAB_PROJECT_NAME=${GITLAB_PROJECT_NAME}"
                        composer (phpVersion: phpVersion,
                                  GITLAB_PROJECT_NAMESPACE: GITLAB_PROJECT_NAMESPACE,
                                  GITLAB_PROJECT_NAME: GITLAB_PROJECT_NAME)
                    }
                }
            }
            stage('Build Docker image') {
                steps {
                    script {
                        dockerImage = buildDocker (namespace: GITLAB_PROJECT_NAMESPACE,
                                                   name: GITLAB_PROJECT_NAME,
                                                   tag: GIT_COMMIT[0..7])
                    }
                }
            }
            stage('Test Docker image structure') {
                when { expression { fileExists 'container-structure-test.yaml' } }
                steps {
                    containerStructureTest (image: dockerImage,
                                            uid: '999',
                                            GITLAB_PROJECT_NAME: GITLAB_PROJECT_NAME,
                                            GITLAB_PROJECT_NAMESPACE: GITLAB_PROJECT_NAMESPACE)
                }
            }
            stage('Push Docker image') {
                steps {
                    pushDocker (image: dockerImage)
                }
            }
            stage('Pull Docker image') {
                when { branch 'master' }
                steps {
                    dockerPull image: dockerImage
                }
            }
            stage('Deploy service to swarm') {
                when { branch 'master' }
                agent { label Constants.productionNodeLabel }
                steps {
                    dockerStackDeploy (stack: GITLAB_PROJECT_NAMESPACE,
                                       service: GITLAB_PROJECT_NAME,
                                       image: dockerImage)
                }
                post {
                    success {
                        notifySlack "${GITLAB_PROJECT_PATH_NAMESPACE} deployed to production"
                    }
                }
            }
        }
        post {
            success { cleanWs() }
            failure { notifySlack "Build failled: ${JOB_NAME} [<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}

def call(Map args = [:]) {
    def dockerImage = null
    def gradleCommand = args.command ?: Constants.gradleDefaultCommand

    pipeline {
        agent { label 'master' }
        environment {
            PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GROUP_NAME = jenkinsJob.getGroup(env.JOB_NAME)
        }
        tools {
            gradle '4'
        }
        stages {
            stage('Run Gradle') {
                steps {
                    script { sh "gradle $gradleCommand" }
                }
            }
            stage('Build Docker image') {
                steps {
                    script {
                        dockerImage =
                            buildDocker (namespace: GROUP_NAME,
                                         name: PROJECT_NAME,
                                         tag: gitHeadShort())
                    }
                }
            }
            stage('Test Docker image structure') {
                when { expression { fileExists 'container-structure-test.yaml' } }
                steps {
                    containerStructureTest image: dockerImage
                }
            }
            stage('Push Docker image') {
                steps {
                    pushDocker image: dockerImage
                }
            }
            stage('Pull Docker image') {
                when {
                    branch 'master'
                    beforeAgent true
                }
                steps {
                    dockerPull image: dockerImage
                }
            }
            stage('Deploy service to swarm') {
                when {
                    branch 'master'
                    beforeAgent true
                }
                agent { label Constants.productionNodeLabel }
                steps {
                    dockerStackDeploy stack: GROUP_NAME, service: PROJECT_NAME, image: dockerImage
                }
                post {
                    success {
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
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

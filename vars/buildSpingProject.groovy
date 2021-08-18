def call(String stack, def Map args = [:]) {
    def dockerImage = null

    pipeline {
        agent { label 'master' }
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
        tools {
            gradle (args.gradle ?: "4")
            jdk (args.java ?: "8")
        }
        stages {
            stage('Build Gradle') {
                when {
                    not { expression { return params.skipToDeploy } }
                    beforeAgent true
                }
                steps {
                    sh "java -version; gradle -version; gradle build"
                }
            }
            stage('Build Docker jdk image') {
                when {
                    allOf {
                        expression { fileExists 'Dockerfile.jdk' }
                        not { expression { return params.skipToDeploy } }
                        not { expression { return params.switchStacks } }
                    }
                }
                steps {
                    script {
                        dockerImage =
                            buildDocker (namespace: GROUP_NAME,
                                         dockerfile: 'Dockerfile.jdk',
                                         name: PROJECT_NAME,
                                         tag: gitHeadShort() + '-jdk')
                    }
                }
            }
            stage('Push Docker jdk image') {
               when {
                    allOf {
                        expression { fileExists 'Dockerfile.jdk' }
                        not { expression { return params.skipToDeploy } }
                        not { expression { return params.switchStacks } }
                    }
                }
                steps {
                    pushDocker image: dockerImage
                }
            }
            stage('Build Docker image') {
                when {
                    not { expression { return params.skipToDeploy } }
                    beforeAgent true
                }
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
                when {
                    allOf {
                        expression { fileExists 'container-structure-test.yaml' }
                        not { expression { return params.skipToDeploy } }
                    }
                }
                steps {
                    containerStructureTest image: dockerImage
                }
            }
            stage('Push Docker image') {
                when {
                    not { expression { return params.skipToDeploy } }
                    beforeAgent true
                }
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
                    dockerStackDeploy stack: stack, service: PROJECT_NAME, image: dockerImage, dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
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

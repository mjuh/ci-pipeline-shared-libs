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
            stage('Build Docker image') {
                when {
                    not { expression { return params.skipToDeploy } }
                    beforeAgent true
                }
                steps {
                    script {
                        dockerImage =
                            nixBuildDocker (namespace: GROUP_NAME,
                                            name: PROJECT_NAME,
                                            tag: gitHeadShort())
                    }
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
                    dockerPull image: dockerImage, nodeLabel: [composeProject]
                }
            }
            stage('Deploy service') {
                when {
                    branch 'master'
                    beforeAgent true
                }
                agent { label composeProject }
                steps {
                    script {
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

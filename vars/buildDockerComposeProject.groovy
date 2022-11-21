def call(String composeProject, Map args = [:]) {
    def dockerImage = null

    pipeline {
        agent { label 'jenkins' }
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
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            timeout(time: 3, unit: "HOURS")
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
                    dockerPull image: dockerImage, nodeLabel: [composeProject]
                }
            }
            stage('Deploy service') {
                when {
                    branch 'master'
                    beforeAgent true
                }
                steps {
                    script {
                        if (params.skipToDeploy == null) {
                            imageName = Constants.dockerRegistryHost + "/" + GITLAB_PROJECT_NAMESPACE + "/" + GITLAB_PROJECT_NAME + ":" + gitCommit().take(8)
                            echo "imageName: ${imageName}"
                            dockerImage = new DockerImageTarball(
                                imageName: imageName,
                                path: "" // XXX: Specifiy path in DockerImageTarball for flake buildWebService.
                            )
                        }
                        sequentialCall (
                            nodeLabels: [composeProject],
                            procedure: { nodeLabels ->
                                ansiColor("xterm") {
                                    if (args.services == null) {
                                        dockerComposeDeploy (
                                            project: composeProject,
                                            service: PROJECT_NAME,
                                            image: dockerImage,
                                            dockerStacksRepoCommitId: params.dockerStacksRepoCommitId,
                                            projectConfigFile: PROJECT_NAME + "-" + env.NODE_NAME + ".yml"
                                        )
                                    } else {
                                        args.services.each { service ->
                                            dockerComposeDeploy (
                                                project: composeProject,
                                                service: service,
                                                image: dockerImage,
                                                dockerStacksRepoCommitId: params.dockerStacksRepoCommitId,
                                                projectConfigFile: PROJECT_NAME + "-" + env.NODE_NAME + ".yml"
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

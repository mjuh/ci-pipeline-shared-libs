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
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
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
            stage('Deploy services') {
                when {
                    branch 'master'
                    beforeAgent true
                }
                steps {
                    sequentialCall (
                        nodeLabels: [composeProject],
                        procedure: { nodeLabels ->
                            String projectConfigFile = "elk-" + env.NODE_NAME + ".yml"
                            ansiColor("xterm") {
                                dockerComposeDeploy (
                                    project: composeProject,
                                    services: args.services == null ? [ PROJECT_NAME ] : args.services,
                                    image: dockerImage,
                                    dockerStacksRepoCommitId: params.dockerStacksRepoCommitId,
                                    projectConfigFile: projectConfigFile
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

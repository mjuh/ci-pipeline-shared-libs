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
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: [ 'Build Gradle','Build Docker image', 'Push Docker image'])
        }
        tools {
            gradle (args.gradle ?: "latest")
        }
        stages {
            stage('Build Gradle') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            switch (args.java) {
                                case '8':
                                    sh 'nix-shell -I nixpkgs=https://github.com/NixOS/nixpkgs/archive/48723f48.tar.gz -p openjdk8 --run "gradle build"' // nixos-20.03
                                    break;
                                case '11':
                                    sh 'nix-shell -I nixpkgs=https://github.com/NixOS/nixpkgs/archive/48723f48.tar.gz -p openjdk11 --run "gradle build"' // nixos-20.03
                                    break;
                                case '14':
                                    sh 'nix-shell -I nixpkgs=https://github.com/NixOS/nixpkgs/archive/4214f76b.tar.gz -p openjdk14 --run "gradle build"' // nixos-unstable
                                    break;
                                default:
                                    sh 'gradle build'
                                    break;
                            }
                        }
                    }
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
                        script { dockerImage = buildDocker namespace: GROUP_NAME, dockerfile: 'Dockerfile.jdk',name: PROJECT_NAME, tag: GIT_COMMIT[0..7]+'-jdk' }
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
                    gitlabCommitStatus(STAGE_NAME) {
                        pushDocker image: dockerImage
                    }
                }
            }
            stage('Build Docker image') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script { dockerImage = buildDocker namespace: GROUP_NAME, name: PROJECT_NAME, tag: GIT_COMMIT[0..7] }
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
                    gitlabCommitStatus(STAGE_NAME) {
                        containerStructureTest image: dockerImage
                    }
                }
            }
            stage('Push Docker image') {
                when { not { expression { return params.skipToDeploy } } }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        pushDocker image: dockerImage
                    }
                }
            }
            stage('Pull Docker image') {
                when { branch 'master' }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerPull image: dockerImage
                    }
                }
            }
            stage('Deploy service to swarm') {
                when { branch 'master' }
                agent { label Constants.productionNodeLabel }
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        dockerStackDeploy stack: stack, service: PROJECT_NAME, image: dockerImage, dockerStacksRepoCommitId: params.dockerStacksRepoCommitId
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

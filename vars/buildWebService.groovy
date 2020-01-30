import groovy.json.JsonOutput

def call() {
    def dockerImages = null

    pipeline {
        agent { label 'nixbld' }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image'])
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
        }
        parameters {
            string(name: 'OVERLAY_BRANCH_NAME',
                   defaultValue: 'master',
                   description: 'Git Branch at https://gitlab.intr/_ci/nixpkgs/ repository')
        }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
            IMAGE_TAG = nixRepoTag overlaybranch: params.OVERLAY_BRANCH_NAME, currentProjectBranch: GIT_BRANCH
            DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GROUP_NAME}/${PROJECT_NAME}/tag/${IMAGE_TAG}"
        }
        stages {
            stage('Build Docker image') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            echo "Building image with ${params.OVERLAY_BRANCH_NAME} branch in https://gitlab.intr/_ci/nixpkgs/tree/${params.OVERLAY_BRANCH_NAME}/"
                            dockerImages = nixBuildDocker namespace: GROUP_NAME, name: PROJECT_NAME, overlaybranch: params.OVERLAY_BRANCH_NAME, currentProjectBranch: GIT_BRANCH
                        }
                    }
                }
            }
            stage('Test Docker image') {
                when { expression { fileExists 'test.nix' } }
                steps {
                    script {
                        def BUILD_CMD_TEMPLATE = [
                            "nix-build", "test.nix",
                            "--argstr", "ref", "${params.OVERLAY_BRANCH_NAME}",
                            "--show-trace"
                        ]

                        def BUILD_CMD = (BUILD_CMD_TEMPLATE + [
                                "--out-link", "test-result"]).join(" ")
                        def BUILD_CMD_DEBUG = (BUILD_CMD_TEMPLATE + [
                                "--arg", "debug", "true",
                                "--out-link", "test-result-debug"
                            ]).join(" ")

                        [BUILD_CMD, BUILD_CMD_DEBUG].each{
                            print("Invoking ${it}")
                            nixSh cmd: it
                        }

                        archiveArtifacts artifacts: "test-result/**"
                    }
                }
            }
            stage("phpinfo difference") {
                when {
                    allOf {
                        expression {
                            fileExists 'test-result/coverage-data/vm-state-dockerNode/deepdiff.html'
                        }
                        expression {
                            fileExists 'test-result/coverage-data/vm-state-dockerNode/deepdiff-with-excludes.html'
                        }
                    }
                }
                steps {
                    script {
                        def json = readJSON file: 'test-result/coverage-data/vm-state-dockerNode/deepdiff.html'
                        def jsonFormat = JsonOutput.toJson(json)
                        prettyJSON = JsonOutput.prettyPrint(jsonFormat)
                        echo "${prettyJSON}"
                    }
                    script {
                        def json = readJSON file: 'test-result/coverage-data/vm-state-dockerNode/deepdiff-with-excludes.html'
                        if (! json.empty)
                        {
                            def jsonFormat = JsonOutput.toJson(json)
                            prettyJSON = JsonOutput.prettyPrint(jsonFormat)
                            echo "${prettyJSON}"
                            // error "phpinfo sample differs phpinfo in container"
                        }
                    }
                }
            }
            stage('Push Docker image') {
                when {
                    not {
                        anyOf {
                            expression { return GIT_BRANCH.startsWith("wip-") }
                            expression { return params.OVERLAY_BRANCH_NAME.startsWith("wip-") }
                        }
                    }
                }
                steps {
                    script {
                        dockerImages.each { pushDocker image: it, pushToBranchName: false }
                    }
                }
                post {
                    success {
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME}:${GIT_BRANCH} pushed to <${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"
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

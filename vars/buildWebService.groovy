import groovy.json.JsonOutput

def call(Map args = [:]) {
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
            string(name: 'UPSTREAM_BRANCH_NAME',
                   defaultValue: 'master',
                   description: 'Git Branch at upstream repository')
            booleanParam(name: 'DEPLOY',
                         defaultValue: true,
                         description: 'Deploy to Docker image to registry')
        }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
            TAG = nixRepoTag (overlaybranch: params.OVERLAY_BRANCH_NAME, currentProjectBranch: GIT_BRANCH)
            DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GROUP_NAME}/${PROJECT_NAME}/tag/${TAG}"
        }
        stages {
            stage('Build Docker image') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            GitRepository majordomo_overlay =
                                new GitRepository (name: "majordomo",
                                                   url: "https://gitlab.intr/_ci/nixpkgs",
                                                   branch: params.OVERLAY_BRANCH_NAME)

                            dockerImage = nixBuildDocker (namespace: GROUP_NAME,
                                                          name: PROJECT_NAME,
                                                          tag: TAG,
                                                          overlay: majordomo_overlay)

                            dockerImageDebug = nixBuildDocker (namespace: GROUP_NAME,
                                                               name: PROJECT_NAME,
                                                               tag: (TAG + "-debug"),
                                                               overlay: majordomo_overlay,
                                                               nixArgs: ["--arg debug true"])
                        }
                    }
                }
            }
            stage('Test Docker image') {
                when { expression { fileExists 'test.nix' } }
                steps {
                    testNix nixArgs: ["--argstr ref $params.OVERLAY_BRANCH_NAME",
                                      "--argstr phpRef $params.UPSTREAM_BRANCH_NAME"]
                    script { (args.testHook ?: { return true })() }
                }
            }
            stage('Scan for CVE') {
                when { expression { fileExists 'JenkinsfileVulnix.groovy' } }
                steps {
                    build (job: "../../security/$PROJECT_NAME/master",
                           parameters: [[$class: "StringParameterValue",
                                         name: "DOCKER_IMAGE",
                                         value: dockerImage.path]])
                }
            }
            stage('Push Docker image') {
                when {
                    allOf {
                        expression { params.DEPLOY }
                        not {
                            anyOf {
                                expression { return GIT_BRANCH.startsWith("wip-") }
                                expression { return params.OVERLAY_BRANCH_NAME.startsWith("wip-") }
                            }
                        }
                    }
                }
                steps {
                    pushDocker (tag: (TAG + "-debug"), extraTags: ['debug'],
                                image: dockerImageDebug)
                    pushDocker (tag: TAG, image: dockerImage)

                }
                post {
                    success {
                        notifySlack "${GROUP_NAME}/${PROJECT_NAME}:${GIT_BRANCH} \
pushed to <${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"
                    }
                }
            }
        }
        post {
            always { nixCleanWS(directory: "${env.WORKSPACE}/result") }
            failure { notifySlack "Build failled: ${JOB_NAME} \
[<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}

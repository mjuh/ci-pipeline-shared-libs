import groovy.json.JsonOutput

def nixPath (n) {
    n ?: System.getenv("NIX_PATH")
}

/**
 * Send notifications based on build status string.
 * Thanks to https://jenkins.io/blog/2017/02/15/declarative-notifications/
 */
def sendNotifications(Map args = [:]) {
    String buildStatus = args.buildStatus ?: 'STARTED'

    // build status of null means successful
    buildStatus = buildStatus ?: 'SUCCESS'

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${JOB_NAME} [${BUILD_ID}]'"
    def summary = "${subject} (${env.BUILD_URL})"

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        color = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESS') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else {
        color = 'RED'
        colorCode = '#FF0000'
    }

    // Send notifications
    if (args.threadMessages) {
        slackSend (color: colorCode,
                   message: ([summary,
                              args.threadMessages.join("\n\n")].join("\n\n")))
    } else {
        slackSend (color: colorCode, message: summary)
    }
}

def call(Map args = [:]) {
    def dockerImages = null
    def slackMessages = [];
    def nixFile = args.nixFile ?: 'default.nix'
    
    pipeline {
        agent { label 'nixbld' }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image'])
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            timeout(time: 1, unit: 'HOURS')
        }
        parameters {
            string(name: 'OVERLAY_BRANCH_NAME',
                   defaultValue: 'master',
                   description: 'Git Branch at https://gitlab.intr/_ci/nixpkgs/ repository')
            string(name: 'UPSTREAM_BRANCH_NAME',
                   defaultValue: 'master',
                   description: 'Git Branch at upstream repository')
            string(name: 'NIX_PATH',
                   defaultValue: '',
                   description: 'Nix expressions ("System.getenv(\"NIX_PATH\")" if empty)')
            booleanParam(name: 'DEPLOY',
                         defaultValue: true,
                         description: 'Deploy Docker image to registry')
            booleanParam(name: "DEBUG",
                         defaultValue: false,
                         description: "Deploy DEBUG image Docker image to registry")
            booleanParam(name: "STACK_DEPLOY",
                         defaultValue: args.stackDeploy ?: false,
                         description: "Deploy Docker image to swarm")
            booleanParam(name: "PUBLISH_ON_INTERNET",
                         defaultValue: args.publishOnInternet ?: false,
                         description: "Publish on GitHub.com")
            string(name: 'NIX_ARGS',
                   defaultValue: "",
                   description: 'Invoke Nix with additional arguments')
        }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
            TAG = nixRepoTag (overlaybranch: params.OVERLAY_BRANCH_NAME, currentProjectBranch: GIT_BRANCH)
            DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GROUP_NAME}/${PROJECT_NAME}/tag/${TAG}"
            NIX_PATH = nixPath params.NIX_PATH
        }
        stages {
            stage('Build Docker image') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            (args.preBuild ?: { return true })()

                            // Workaround for error: cannot lock ref
                            // 'refs/heads/testbranch': is at 02cd73... but
                            // expected ea35c7...
                            sh "git gc --prune=now"

                            String nixVersionCmd = "nix-instantiate --eval --expr '(import <nixpkgs> {}).lib.version'"
                            slackMessages += String
                                .format("$nixVersionCmd\n=> %s",
                                        (sh (script: nixVersionCmd,
                                             returnStdout: true)).trim())

                            GitRepository majordomo_overlay =
                                new GitRepository (name: "majordomo",
                                                   url: "https://gitlab.intr/_ci/nixpkgs",
                                                   branch: params.OVERLAY_BRANCH_NAME)

                            slackMessages += String
                                .format("Overlay: %s",
                                        (majordomo_overlay.url
                                         + "/tree/" + majordomo_overlay.branch))

                            dockerImage = nixBuildDocker (namespace: GROUP_NAME,
                                                          name: PROJECT_NAME,
                                                          tag: TAG,
                                                          overlay: majordomo_overlay,
                                                          nixFile: nixFile,
                                                          nixArgs: [params.NIX_ARGS])

                            dockerImageDebug = nixBuildDocker (namespace: GROUP_NAME,
                                                               name: PROJECT_NAME,
                                                               tag: (TAG + "-debug"),
                                                               overlay: majordomo_overlay,
                                                               nixArgs: (["--arg debug true"] +
                                                                         [params.NIX_ARGS]))

                            (args.postBuild ?: { return true })()
                        }
                    }
                }
            }
            stage('Test Docker image') {
                when { expression { fileExists 'test.nix' } }
                steps {
                    testNix (nixArgs: (["--argstr ref $params.OVERLAY_BRANCH_NAME",
                                       "--argstr phpRef $params.UPSTREAM_BRANCH_NAME"]
                                       + [params.NIX_ARGS]),
                             nixFile: "test")
                    script { (args.testHook ?: { return true })() }
                }
            }
            stage('Test Docker image without sandbox') {
                when { expression { fileExists 'test-no-sandbox.nix' } }
                steps {
                    testNix (nixArgs: (["--argstr ref $params.OVERLAY_BRANCH_NAME",
                                        "--argstr phpRef $params.UPSTREAM_BRANCH_NAME",
                                        "--option sandbox false"]
                                       + [params.NIX_ARGS]),
                             nixFile: "test-no-sandbox")
                    script { (args.testNoSandboxHook ?: { return true })() }
                }
            }
            stage('Scan for CVE') {
                when { allOf {
                        expression { fileExists 'JenkinsfileVulnix.groovy' }
                        expression { majordomo_overlay.branch == "master" }
                        branch "master"
                    }
                }
                steps {
                    build (job: "../../security/$PROJECT_NAME/master",
                           parameters: [[$class: "StringParameterValue",
                                         name: "DOCKER_IMAGE",
                                         value: dockerImage.path]])
                }
            }
            stage("Scan for passwords in Git history") {
                steps {
                    build (
                        job: "../../ci/bfg/master",
                        parameters: [string(
                                name: "GIT_REPOSITORY_TARGET_URL",
                                value: gitRemoteOrigin.getRemote().url
                            )
                        ]
                    )
                }
            }
            stage("Deploy") {
                when {
                    allOf {
                        expression { params.DEPLOY }
                        not {
                            anyOf {
                                triggeredBy('TimerTrigger')
                                expression { return GIT_BRANCH.startsWith("wip-") }
                                expression { return params.OVERLAY_BRANCH_NAME.startsWith("wip-") }
                            }
                        }
                    }
                }
                steps {
                    pushDocker (tag: TAG, image: dockerImage)
                    script {
                        if (params.DEBUG) {
                            pushDocker (tag: (TAG + "-debug"), extraTags: ['debug'],
                                        image: dockerImageDebug)
                        }

                        slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"
                        slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}-debug|${DOCKER_REGISTRY_BROWSER_URL}-debug>"

                        // Deploy to Docker Swarm
                        if (args.stackDeploy && TAG == "master" && params.STACK_DEPLOY &&
                            !(currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause'))) {
                            node(Constants.productionNodeLabel) {
                                dockerStackDeploy (
                                    stack: GROUP_NAME,
                                    service: PROJECT_NAME,
                                    image: dockerImage
                                )
                                slackMessages += "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                            }
                        }
                        ({ value -> value in String ? slackMessages += value : value })((args.postPush ?: { return true })())
                    }
                }
            }
            stage("Publish on the Internet") {
                when {
                    allOf {
                        expression { params.PUBLISH_ON_INTERNET }
                        not { triggeredBy("TimerTrigger") }
                        expression { params.OVERLAY_BRANCH_NAME == "master" }
                        branch "master"
                    }
                }
                steps {
                    script {
                        comGithub.push group: GROUP_NAME, name: PROJECT_NAME
                        slackMessages += "${GROUP_NAME}/${PROJECT_NAME} pushed to github.com"
                    }
                }
            }
        }
        post {
            always {
                nixCleanWS(directory: "${env.WORKSPACE}/result")
                sendNotifications (buildStatus: currentBuild.result,
                                   threadMessages: slackMessages)
            }
        }
    }
}

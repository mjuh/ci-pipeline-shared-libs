import groovy.json.JsonOutput

def nixPath (n) {
    n ?: System.getenv("NIX_PATH")
}

def call(Map args = [:]) {
    def dockerImages = null
    def slackMessages = [];
    def nixFile = args.nixFile ?: 'default.nix'
    Boolean scanPasswords = args.scanPasswords == null ? true : args.scanPasswords
    
    pipeline {
        agent { label 'nixbld' }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ['Build Docker image'])
            buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
            timeout(time: 6, unit: 'HOURS')
        }
        parameters {
            string(name: 'OVERLAY_BRANCH_NAME',
                   defaultValue: 'master',
                   description: "Git Branch at $Constants.nixOverlay repository")
            string(name: 'UPSTREAM_BRANCH_NAME',
                   defaultValue: 'master',
                   description: 'Git Branch at upstream repository')
            string(name: 'NIX_PATH',
                   defaultValue: '',
                   description: 'Nix expressions ("System.getenv(\"NIX_PATH\")" if empty)')
            booleanParam(name: 'DEPLOY',
                         defaultValue: true,
                         description: 'Deploy Docker image to registry')
            booleanParam(name: "STACK_DEPLOY",
                         defaultValue: args.stackDeploy ?: false,
                         description: "Deploy Docker image to swarm")
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
                            // Workaround for error: cannot lock ref
                            // 'refs/heads/testbranch': is at 02cd73... but
                            // expected ea35c7...
                            sh "git gc --prune=now"

                            String nixVersionCmd = "nix-instantiate --eval --expr '(import <nixpkgs> {}).lib.version'"
                            nixVersion = (sh (script: nixVersionCmd, returnStdout: true)).trim()
                            slackMessages += String
                                .format("$nixVersionCmd\n=> %s", nixVersion)

                            if (TAG == "master") {
                                buildBadge = addEmbeddableBadgeConfiguration(
                                    id: (GROUP_NAME + "-" + PROJECT_NAME),
                                    subject: "<nixpkgs>: $nixVersion"
                                )
                                buildBadge.setStatus('running')
                            }

                            (args.preBuild ?: { return true })()

                            majordomo_overlay =
                                new GitRepository (name: "majordomo",
                                                   url: Constants.nixOverlay,
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

                            if (args.debug) {
                                dockerImageDebug = nixBuildDocker (namespace: GROUP_NAME,
                                                                   name: PROJECT_NAME,
                                                                   tag: (TAG + "-debug"),
                                                                   overlay: majordomo_overlay,
                                                                   nixArgs: (["--arg debug true"] +
                                                                             [params.NIX_ARGS]))
                            }

                            (args.postBuild ?: { return true })()
                        }
                    }
                }
            }
            stage('Test Docker image') {
                when { expression { fileExists 'test.nix' } }
                steps {
                    testNix (nixArgs: (["--argstr overlayUrl $majordomo_overlay.url",
                                        "--argstr overlayRef $majordomo_overlay.branch",
                                        "--argstr phpRef $params.UPSTREAM_BRANCH_NAME"]
                                       + [params.NIX_ARGS]),
                             nixFile: "test")
                    script { (args.testHook ?: { return true })() }
                }
            }
            stage('Test Docker image without sandbox') {
                when { expression { fileExists 'test-no-sandbox.nix' } }
                steps {
                    testNix (nixArgs: (["--argstr overlayUrl $majordomo_overlay.url",
                                        "--argstr overlayRef $majordomo_overlay.branch",
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
                when { expression { scanPasswords } }
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
                                expression { return majordomo_overlay.branch.startsWith("wip-") }
                            }
                        }
                    }
                }
                steps {
                    pushDocker (tag: TAG, image: dockerImage)
                    script {
                        slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"

                        if (args.debug) {
                            pushDocker (tag: (TAG + "-debug"), extraTags: ['debug'],
                                        image: dockerImageDebug)
                            slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}-debug|${DOCKER_REGISTRY_BROWSER_URL}-debug>"
                        }

                        // Deploy to Docker Swarm
                        if (args.stackDeploy && TAG == "master" && params.STACK_DEPLOY &&
                            !(currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause'))) {
                            node(Constants.productionNodeLabel) {
                                slackMessages += dockerStackDeploy (
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
                        expression { args.publishOnInternet }
                        not { triggeredBy("TimerTrigger") }
                        expression { majordomo_overlay.branch == "master" }
                        branch "master"
                    }
                }
                steps {
                    script {
                        comGithub.push group: GROUP_NAME, name: PROJECT_NAME
                        slackMessages += "Pushed to https://github.com/${Constants.githubOrganization}/${GROUP_NAME}-${PROJECT_NAME}"
                    }
                }
            }
        }
        post {
            success {
                script {
                    if (TAG == "master") {
                        buildBadge.setStatus("passing")
                    }
                }
            }
            failure {
                script {
                    if (TAG == "master") {
                        buildBadge.setStatus("failing")
                        buildBadge.setColor('pink')
                    }
                }
            }
            always {
                nixCleanWS(directory: "${env.WORKSPACE}/result")
                sendSlackNotifications (buildStatus: currentBuild.result,
                                        threadMessages: slackMessages)
            }
        }
    }
}

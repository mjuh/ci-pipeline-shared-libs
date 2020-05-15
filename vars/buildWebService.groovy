import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.JsonOutput

def nixPath (n) {
    n ?: System.getenv("NIX_PATH")
}

def call(Map args = [:]) {
    def dockerImages = null
    def slackMessages = [];
    def nixFile = args.nixFile ?: "default.nix"
    Boolean scanPasswords = args.scanPasswords == null ? true : args.scanPasswords
    
    pipeline {
        agent { label "nixbld" }
        options {
            gitLabConnection(Constants.gitLabConnection)
            gitlabBuilds(builds: ["Build Docker image"])
            timeout(time: 6, unit: "HOURS")
            buildDiscarder(
                logRotator(numToKeepStr: "10", artifactNumToKeepStr: "10")
            )
        }
        parameters {
            string(name: "OVERLAY_BRANCH_NAME",
                   defaultValue: "master",
                   description: "Git Branch at $Constants.nixOverlay repository")
            string(name: "UPSTREAM_BRANCH_NAME",
                   defaultValue: "master",
                   description: "Git Branch at upstream repository")
            string(name: "NIX_PATH",
                   defaultValue: "",
                   description: 'Nix expressions ("System.getenv(\"NIX_PATH\")" if empty)')
            booleanParam(name: "DEPLOY",
                         defaultValue: true,
                         description: "Deploy Docker image to registry")
        }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
            DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GROUP_NAME}/${PROJECT_NAME}/tag/${TAG}"
            NIX_PATH = nixPath params.NIX_PATH
            TAG = nixRepoTag (
                overlaybranch: params.OVERLAY_BRANCH_NAME,
                currentProjectBranch: GIT_BRANCH
            )
        }
        stages {
            stage("Build") {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        script {
                            parallel (
                                ["Build container": {
                                        String nixVersion = nix.version()
                                        slackMessages += String
                                            .format("nixpkgs version: %s",
                                                    nixVersion)

                                        if (TAG == "master") {
                                            buildBadge = addEmbeddableBadgeConfiguration(
                                                id: (GROUP_NAME + "-" + PROJECT_NAME),
                                                subject: "<nixpkgs>: $nixVersion"
                                            )
                                            buildBadge.setStatus("running")
                                        }

                                        (args.preBuild ?: { return true })()

                                        majordomo_overlay = new GitRepository(
                                            name: "majordomo",
                                            url: Constants.nixOverlay,
                                            branch: params.OVERLAY_BRANCH_NAME
                                        )

                                        slackMessages += String
                                            .format("Overlay: %s",
                                                    (majordomo_overlay.url
                                                     + "/tree/" + majordomo_overlay.branch))

                                        dockerImage = nixBuildDocker(
                                            namespace: GROUP_NAME,
                                            name: PROJECT_NAME,
                                            tag: TAG,
                                            overlay: majordomo_overlay,
                                            nixFile: nixFile
                                        )

                                        if (args.debug) {
                                            dockerImageDebug = nixBuildDocker(
                                                namespace: GROUP_NAME,
                                                name: PROJECT_NAME,
                                                tag: (TAG + "-debug"),
                                                overlay: majordomo_overlay,
                                                nixArgs: ["--arg debug true"]
                                            )
                                        }

                                        (args.postBuild ?: { return true })()
                                    },

                                 "Scan passwords in Git history": {
                                        if (scanPasswords) {
                                            build (
                                                job: "../../ci/bfg/master",
                                                parameters: [string(
                                                        name: "GIT_REPOSITORY_TARGET_URL",
                                                        value: gitRemoteOrigin.getRemote().url
                                                    )
                                                ]
                                            )
                                        } else {
                                            Utils.markStageSkippedForConditional("Scan passwords in Git history")
                                        }
                                    }
                                ]
                            )
                        }
                    }
                }
            }
            stage("Test") {
                steps {
                    script {
                        parallel (
                            ["Check": {
                                    Boolean runTest = fileExists("test.nix")
                                    Boolean runTestWithoutSandbox =
                                        fileExists("test-no-sandbox.nix")
                                    if (runTest) {
                                        testNix (
                                            nixArgs: (
                                                ["--argstr overlayUrl $majordomo_overlay.url",
                                                 "--argstr overlayRef $majordomo_overlay.branch",
                                                 "--argstr phpRef $params.UPSTREAM_BRANCH_NAME"
                                                ]
                                            ),
                                            nixFile: "test"
                                        )
                                    }
                                    if (runTestWithoutSandbox) {
                                        testNix (
                                            nixArgs: (
                                                ["--argstr overlayUrl $majordomo_overlay.url",
                                                 "--argstr overlayRef $majordomo_overlay.branch",
                                                 "--argstr phpRef $params.UPSTREAM_BRANCH_NAME",
                                                 "--option sandbox false"]
                                            ),
                                            nixFile: "test-no-sandbox"
                                        )
                                    }
                                    Boolean testHook = (args.testHook ?: { return true })()
                                    runTest || runTestWithoutSandbox || testHook || Utils.markStageSkippedForConditional("Check")
                                },
                             "Check CVE": {
                                    if ((fileExists("JenkinsfileVulnix.groovy") &&
                                         TAG == "master")) {
                                        build (job: "../../security/$PROJECT_NAME/master",
                                               parameters: [[$class: "StringParameterValue",
                                                             name: "DOCKER_IMAGE",
                                                             value: dockerImage.path]])
                                    } else {
                                        Utils.markStageSkippedForConditional("Check CVE")
                                    }
                                }
                            ]
                        )
                    }
                }
            }
            stage("Deploy") {
                when {
                    allOf {
                        expression { params.DEPLOY }
                        not { triggeredBy("TimerTrigger") }
                    }
                }
                steps {
                    script {
                        parallel (
                            ["Deploy container": {
                                    pushDocker (tag: TAG, image: dockerImage)
                                    slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"

                                    if (args.debug) {
                                        pushDocker (
                                            tag: (TAG + "-debug"), extraTags: ["debug"],
                                            image: dockerImageDebug
                                        )
                                        slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}-debug|${DOCKER_REGISTRY_BROWSER_URL}-debug>"
                                    }

                                    // Deploy to Docker Swarm
                                    if (args.stackDeploy && TAG == "master") {
                                        node(Constants.productionNodeLabel) {
                                            slackMessages += dockerStackDeploy (
                                                stack: GROUP_NAME,
                                                service: PROJECT_NAME,
                                                image: dockerImage
                                            )
                                            slackMessages += "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                                        }
                                    }

                                    ({ value -> value in String ? slackMessages += value : value })
                                    ((args.postPush ?: { return true })())
                                },
                             "Push to GitHub": {
                                    if (args.publishOnInternet) {
                                        comGithub.push(
                                            group: GROUP_NAME,
                                            name: PROJECT_NAME
                                        )
                                        slackMessages += "Pushed to https://github.com/${Constants.githubOrganization}/${GROUP_NAME}-${PROJECT_NAME}"
                                    } else {
                                        Utils.markStageSkippedForConditional("Push to GitHub")
                                    }
                                }
                            ]
                        )
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
                        buildBadge.setColor("pink")
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

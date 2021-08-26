import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.JsonOutput

def nixPath (n) {
    n ?: System.getenv("NIX_PATH")
}

def projectName (Map args = [:]) {
    args.projectName == null ? gitRemoteOrigin.getProject() : args.projectName
}

def call(Map args = [:]) {
    if (args.flake == true) {
        def slackMessages = [];

        pipeline {
            agent { label "jenkins" }
            options {
                timeout(time: 6, unit: "HOURS")
            }
            environment {
            	GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            	GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
		GITLAB_PROJECT_PATH_NAMESPACE = "${GITLAB_PROJECT_NAMESPACE}/${GITLAB_PROJECT_NAME}"
                DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GITLAB_PROJECT_PATH_NAMESPACE}/tag/${TAG}"
                NIX_PATH="nixpkgs=https://github.com/NixOS/nixpkgs/archive/d5291756487d70bc336e33512a9baf9fa1788faf.tar.gz"
            }
            stages {
                stage("build") {
                    steps {
                            script {
                                (args.preBuild ?: { return true })()

                                outLink = "result/${env.JOB_NAME}/docker-${env.BUILD_NUMBER}"
                                sh (nix.shell (run: ((["nix", "build"]
                                                      + Constants.nixFlags
                                                      + ["--out-link", outLink, ".#container"]
                                                      + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                                dockerImage = new DockerImageTarball(
                                  imageName: (Constants.dockerRegistryHost + "/" + GITLAB_PROJECT_NAMESPACE + "/" + GITLAB_PROJECT_NAME + ":" + gitTag()),
                                  path: outLink
                                )
                            }
                    }
                }
                stage("tests") {
                    steps {
                        script {
                                parallel (["nix flake check": {
                                            ansiColor("xterm") {
                                                sh (nix.shell (run: ((["nix flake check"]
                                                                      + Constants.nixFlags
                                                                      + (args.nixArgs == null ? [] : args.nixArgs)
                                                                      + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                                      + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))}}]
                                          + (args.scanPasswords == true ?
                                             ["bfg": {
                                                build (job: "../../${Constants.bfgJobName}/master",
                                                       parameters: [
                                                        string(name: "GIT_REPOSITORY_TARGET_URL",
                                                               value: gitRemoteOrigin.getRemote().url),
                                                        string(name: "PROJECT_NAME",
                                                               value: GITLAB_PROJECT_NAME),
                                                        string(name: "GROUP_NAME",
                                                               value: GITLAB_PROJECT_NAMESPACE),
                                                    ])}]
                                             : [:]))

                                Boolean testHook = (args.testHook ?: { return true })([input: [image: dockerImage]])
                                testHook || Utils.markStageSkippedForConditional("tests")
                            }
                    }
                }
                stage("deploy") {
                    steps {
                            script {
                                sh (nix.shell (run: ((["nix", "run"]
                                                      + Constants.nixFlags
                                                      + [".#deploy"]
                                                      + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                                slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"

                                // Deploy to Docker Swarm
                                if (args.stackDeploy && GIT_BRANCH == "master") {
                                    node(Constants.productionNodeLabel) {
                                        slackMessages += dockerStackDeploy (
                                            stack: GITLAB_PROJECT_NAMESPACE,
                                            service: GITLAB_PROJECT_NAME,
                                            image: dockerImage
                                        )
                                        slackMessages += "${GITLAB_PROJECT_NAMESPACE}/${GITLAB_PROJECT_NAME} deployed to production"
                                    }
                                }

                            }
                    }
                }
            }
            post {
                always {
                    sendSlackNotifications (buildStatus: currentBuild.result,
                                            threadMessages: slackMessages)
                }
            }
        }
    }
    else {
        def dockerImages = null
        def slackMessages = [];
        def nixFile = args.nixFile ?: "default.nix"
        Boolean scanPasswords = args.scanPasswords == null ? true : args.scanPasswords
        
        pipeline {
            agent { label "nixbld" }
            options {
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
            	GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            	GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
		GITLAB_PROJECT_PATH_NAMESPACE = "${GITLAB_PROJECT_NAMESPACE}/${GITLAB_PROJECT_NAME}"
                DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GITLAB_PROJECT_PATH_NAMESPACE}/tag/${TAG}"
                NIX_PATH = nixPath params.NIX_PATH
                TAG = nixRepoTag (
                    overlaybranch: params.OVERLAY_BRANCH_NAME,
                    currentProjectBranch: GIT_BRANCH
                )
            }
            stages {
                stage("Build") {
                    steps {
                            script {
                                parallel (
                                    ["Build container": {
                                            String nixVersion = nix.version()
                                            slackMessages += String
                                                .format("nixpkgs version: %s",
                                                        nixVersion)

                                            if (TAG == "master") {
                                                buildBadge = addEmbeddableBadgeConfiguration(
                                                    id: (GITLAB_PROJECT_NAMESPACE + "-" + GITLAB_PROJECT_NAME),
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
                                                namespace: GITLAB_PROJECT_NAMESPACE,
                                                name: GITLAB_PROJECT_NAME,
                                                tag: TAG,
                                                overlay: majordomo_overlay,
                                                nixFile: nixFile,
                                                saveResult: args.saveResult
                                            )

                                            if (args.debug) {
                                                dockerImageDebug = nixBuildDocker(
                                                    namespace: GITLAB_PROJECT_NAMESPACE,
                                                    name: GITLAB_PROJECT_NAME,
                                                    tag: (TAG + "-debug"),
                                                    overlay: majordomo_overlay,
                                                    nixArgs: ["--arg debug true"],
                                                    saveResult: args.saveResult
                                                )
                                            }

                                            (args.postBuild ?: { return true })([input: [image: dockerImage,
                                                                                         overlay: majordomo_overlay]])
                                        },

                                     "Scan passwords in Git history": {
                                            if (scanPasswords) {
                                                build (
                                                    job: "../../${Constants.bfgJobName}/master",
                                                    parameters: [
                                                        string(name: "GIT_REPOSITORY_TARGET_URL",
                                                               value: gitRemoteOrigin.getRemote().url),
                                                        string(name: "PROJECT_NAME",
                                                               value: GITLAB_PROJECT_NAME),
                                                        string(name: "GROUP_NAME",
                                                               value: GITLAB_PROJECT_NAMESPACE),
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
                                            Boolean testHook = (args.testHook ?: { return true })([input: [image: dockerImage]])
                                            runTest || runTestWithoutSandbox || testHook || Utils.markStageSkippedForConditional("Check")
                                        },
                                     "Check CVE": {
                                            if ((fileExists("JenkinsfileVulnix.groovy") &&
                                                 TAG == "master")) {
                                                build (job: "../../security/$GITLAB_PROJECT_NAME/master",
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
                                                tag: (TAG + "-debug"),
                                                extraTags: ["debug"],
                                                image: dockerImageDebug
                                            )
                                            slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}-debug|${DOCKER_REGISTRY_BROWSER_URL}-debug>"
                                        }

                                        // Deploy to Docker Swarm
                                        if (args.stackDeploy && TAG == "master") {
                                            node(Constants.productionNodeLabel) {
                                                slackMessages += dockerStackDeploy (
                                                    stack: GITLAB_PROJECT_NAMESPACE,
                                                    service: GITLAB_PROJECT_NAME,
                                                    image: dockerImage
                                                )
                                                slackMessages += "${GITLAB_PROJECT_PATH_NAMESPACE} deployed to production"
                                            }
                                        }

                                        ({ value -> value in String ? slackMessages += value : value })
                                        ((args.postPush ?: { return true })([input: [image: dockerImage]]))
                                    },
                                 "Push to GitHub": {
                                        if (args.publishOnInternet && TAG == "master") {
                                            comGithub.push(
                                                group: GITLAB_PROJECT_NAMESPACE,
                                                name: GITLAB_PROJECT_NAME
                                            )
                                            slackMessages += "Pushed to https://github.com/${Constants.githubOrganization}/${GITLAB_PROJECT_NAMESPACE}-${GITLAB_PROJECT_NAME}"
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
}

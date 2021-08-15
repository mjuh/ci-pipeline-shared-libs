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
            agent { label "master" }
            options {
                gitLabConnection(Constants.gitLabConnection)
                gitlabBuilds(builds: ["build", "tests", "deploy"])
                timeout(time: 6, unit: "HOURS")
            }
            environment {
                PROJECT_NAME = projectName(projectName: args.projectName)
                GROUP_NAME = gitRemoteOrigin.getGroup()
                DOCKER_REGISTRY_BROWSER_URL = "${Constants.dockerRegistryBrowserUrl}/repo/${GROUP_NAME}/${PROJECT_NAME}/tag/${TAG}"
                NIX_PATH="nixpkgs=https://github.com/NixOS/nixpkgs/archive/d5291756487d70bc336e33512a9baf9fa1788faf.tar.gz"
            }
            stages {
                stage("build") {
                    steps {
                        gitlabCommitStatus(STAGE_NAME) {
                            script {
                                (args.preBuild ?: { return true })()

                                sh (nix.shell (run: ((["nix", "build"]
                                                      + Constants.nixFlags
                                                      + ["--out-link", "result/${env.JOB_NAME}/docker-${env.BUILD_NUMBER}", ".#container"]
                                                      + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                            }
                        }
                    }
                }
                stage("tests") {
                    steps {
                        script {
                            gitlabCommitStatus(STAGE_NAME) {
                                parallel (["nix flake check": {
                                            ansiColor("xterm") {
                                                sh (nix.shell (run: ((["nix flake check"]
                                                                      + Constants.nixFlags
                                                                      + (args.nixArgs == null ? [] : args.nixArgs)
                                                                      + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                                      + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))}}]
                                          + (args.scanPasswords == true ?
                                             ["bfg": {
                                                build (job: "../../ci/bfg/master",
                                                       parameters: [
                                                        string(name: "GIT_REPOSITORY_TARGET_URL",
                                                               value: gitRemoteOrigin.getRemote().url),
                                                        string(name: "PROJECT_NAME",
                                                               value: PROJECT_NAME),
                                                        string(name: "GROUP_NAME",
                                                               value: GROUP_NAME),
                                                    ])}]
                                             : [:]))
                                Boolean testHook = (args.testHook ?: { return true })()
                                testHook || Utils.markStageSkippedForConditional("tests")
                            }
                        }
                    }
                }
                stage("deploy") {
                    steps {
                        gitlabCommitStatus(STAGE_NAME) {
                            script {
                                sh (nix.shell (run: ((["nix", "run"]
                                                      + Constants.nixFlags
                                                      + [".#deploy"]
                                                      + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                                slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"

                                dockerImage = new DockerImageTarball(
                                    imageName: (Constants.dockerRegistryHost + "/" + GROUP_NAME + "/" + PROJECT_NAME + ":" + gitTag()),
                                    path: "" // XXX: Specifiy path in DockerImageTarball for flake buildWebService.
                                )

                                // Deploy to Docker Swarm
                                if (args.stackDeploy && GIT_BRANCH == "master") {
                                    node(Constants.productionNodeLabel) {
                                        slackMessages += dockerStackDeploy (
                                            stack: GROUP_NAME,
                                            service: PROJECT_NAME,
                                            image: dockerImage
                                        )
                                        slackMessages += "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                                    }
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
                gitLabConnection(Constants.gitLabConnection)
                gitlabBuilds(builds: ["Build", "Test"])
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
                PROJECT_NAME = projectName(projectName: args.projectName)
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
                                                nixFile: nixFile,
                                                saveResult: args.saveResult
                                            )

                                            if (args.debug) {
                                                dockerImageDebug = nixBuildDocker(
                                                    namespace: GROUP_NAME,
                                                    name: PROJECT_NAME,
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
                                                    job: "../../ci/bfg/master",
                                                    parameters: [
                                                        string(name: "GIT_REPOSITORY_TARGET_URL",
                                                               value: gitRemoteOrigin.getRemote().url),
                                                        string(name: "PROJECT_NAME",
                                                               value: PROJECT_NAME),
                                                        string(name: "GROUP_NAME",
                                                               value: GROUP_NAME),
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
                        gitlabCommitStatus(STAGE_NAME) {
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
                                                    stack: GROUP_NAME,
                                                    service: PROJECT_NAME,
                                                    image: dockerImage
                                                )
                                                slackMessages += "${GROUP_NAME}/${PROJECT_NAME} deployed to production"
                                            }
                                        }

                                        ({ value -> value in String ? slackMessages += value : value })
                                        ((args.postPush ?: { return true })([input: [image: dockerImage]]))
                                    },
                                 "Push to GitHub": {
                                        if (args.publishOnInternet && TAG == "master") {
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
}

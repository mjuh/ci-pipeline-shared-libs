import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.JsonOutput

def call(Map args = [:]) {
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
        }
        stages {
            stage("build") {
                steps {
                    script {
                        (args.preBuild ?: { return true })()

                        if (args.buildPhase) {
                            ansiColor("xterm") {
                                args.buildPhase(args)
                            }
                        } else {
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
                        if (args.tests == false) {
                            return 0
                        }
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
                        Boolean testHook = (args.testHook ?: { return true })()
                        testHook || Utils.markStageSkippedForConditional("tests")
                    }
                }
            }
            stage("deploy") {
                steps {
                    script {
                        lock("docker-registry") {
                            sh (nix.shell (run: ((["nix", "run"]
                                                  + Constants.nixFlags
                                                  + [".#deploy"]
                                                  + (args.nixArgs == null ? [] : args.nixArgs)).join(" "))))
                            slackMessages += "<${DOCKER_REGISTRY_BROWSER_URL}|${DOCKER_REGISTRY_BROWSER_URL}>"

                            dockerImage = new DockerImageTarball(
                                imageName: (Constants.dockerRegistryHost + "/" + GITLAB_PROJECT_NAMESPACE + "/" + GITLAB_PROJECT_NAME + ":" + gitTag()),
                                path: "" // XXX: Specifiy path in DockerImageTarball for flake buildWebService.
                            )

                            // Deploy to Docker Swarm
                            if (args.stackDeploy && GIT_BRANCH == "master") {
                                dockerStackServices = [ GITLAB_PROJECT_NAME ] + (args.extraDockerStackServices == null ? [] : args.extraDockerStackServices)
                                node(Constants.productionNodeLabel) {
                                    dockerStackServices.each { service ->
                                        slackMessages += dockerStackDeploy (
                                            stack: GITLAB_PROJECT_NAMESPACE,
                                            service: service,
                                            image: dockerImage
                                        )
                                        slackMessages += "${GITLAB_PROJECT_NAMESPACE}/${GITLAB_PROJECT_NAME} deployed to production"
                                    }
                                }
                            }

                            (args.postDeploy ?: { return true })([input: [
                                image: dockerImage,
                                PROJECT_NAME: GITLAB_PROJECT_NAME]])
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

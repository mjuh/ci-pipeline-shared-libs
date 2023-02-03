import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.JsonOutput

def kustomize(command) {
    sh "nix develop git+https://gitlab.intr/nixos/kubernetes --command kustomize ${command.join(" ")}"
}

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
                        imageName = Constants.dockerRegistryHost + "/" + GITLAB_PROJECT_NAMESPACE + "/" + GITLAB_PROJECT_NAME + ":" + gitCommit().take(8)

                        (args.preBuild ?: { return true })()

                        nixFlakeLockUpdate (inputs: ["ssl-certificates"])

                        if (args.buildPhase) {
                            ansiColor("xterm") {
                                args.buildPhase(args)
                            }
                        } else {
                            result = "result/${env.JOB_NAME}/docker-${env.BUILD_NUMBER}"
                            sh (nix.shell (run: ((["nix", "build"]
                                                  + Constants.nixFlags
                                                  + ["--out-link", result, ".#container"]
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
                        (args.preTest ?: { return true })([result: result, imageName: imageName])
                        commit = gitCommit().take(8)
                        parallel (["nix flake check": {
                                     ansiColor("xterm") {
                                         sh (nix.shell (run: ((["nix flake check"]
                                                               + Constants.nixFlags
                                                               + (args.nixArgs == null ? [] : args.nixArgs)
                                                               + (args.printBuildLogs == true ? ["--print-build-logs"] : [])
                                                               + (args.showTrace == true ? ["--show-trace"] : [])).join(" "))))}},
                                   "container structure test": {
                                      if (fileExists ("container-structure-test.yaml")) {
                                          containerStructureTest (namespace: GITLAB_PROJECT_NAMESPACE,
                                                                  name: GITLAB_PROJECT_NAME,
                                                                  tag: commit)
                                      } else {
                                          return true
                                      }
                                    }]
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
                                imageName: imageName,
                                path: "" // XXX: Specifiy path in DockerImageTarball for flake buildWebService.
                            )
                        }

                        if (true) { //GIT_BRANCH == "master"
                            // Deploy to Kubernetes via FluxCD.
                            if (args.fluxcd.enabled) {
                                lock("git@gitlab.intr:cd/fluxcd") {
                                    dir("fluxcd") {
                                        checkout([$class: 'GitSCM',
                                                  userRemoteConfigs: [[url: "git@gitlab.intr:cd/fluxcd"]]])
                                        (args.fluxcd.clusters ? args.fluxcd.clusters : Constants.kubernetesClusters).each { cluster ->
                                            dir("${args.fluxcd.type}/${cluster}/${args.fluxcd.project.name}") {
                                                sh "git reset --hard origin/master"
                                                kustomize(["edit", "set", "image", imageName])
                                                sh """
                                                       if ! git diff --exit-code kustomization.yaml
                                                       then
                                                           git add kustomization.yaml
                                                           git commit --message='infrastructure: ${cluster}: nixos: Update image to ${imageName}.'
                                                           git push --verbose origin HEAD:refs/heads/${args.fluxcd.type}-${cluster}-${args.fluxcd.project.name}-${commit}
                                                       fi
                                                       """
                                            }
                                        }
                                    }
                                }
                            }

                            // Deploy to Docker Swarm.
                            if (args.stackDeploy) {
                                if (args.dockerStackServices == null) {
                                    dockerStackServices = [ GITLAB_PROJECT_NAME ] + (args.extraDockerStackServices == null ? [] : args.extraDockerStackServices)
                                } else {
                                    dockerStackServices = args.dockerStackServices
                                }
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
                        }

                        nix.commitAndPushFlakeLock()

                        (args.postDeploy ?: { return true })([input: [
                            image: dockerImage,
                            PROJECT_NAME: GITLAB_PROJECT_NAME]])
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

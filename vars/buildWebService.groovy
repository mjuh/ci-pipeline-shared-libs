import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.JsonOutput

def kustomize(command) {
    sh "nix develop git+https://gitlab.intr/nixos/kubernetes --command kustomize ${command.join(" ")}"
}

def call(Map args = [:]) {
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
        }
    }
}

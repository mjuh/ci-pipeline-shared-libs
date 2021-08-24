def call(Map args = [:]) {
    pipeline {
        agent { label "kvm-template-builder" }
        options {
            buildDiscarder(logRotator(numToKeepStr: "10", artifactNumToKeepStr: "10"))
            disableConcurrentBuilds()
            timeout(time: 6, unit: "HOURS")
        }
        stages {
            stage("Build") {
                steps {
                    script {
                        flake = "show-packer-build" + "-" + args.distribution + "-" + args.release + (args.administration == null ? "" : "-administration")
                        (sh(returnStdout: true,
                            script: """
                                    nix-shell --packages nixUnstable --run 'nix run \"${Constants.kvmTemplates}#${flake}\" --impure'
                                    """).trim().split("\n")).each { command ->
                                                                    ansiColor("xterm") {
                                                                         sh(["PACKER_LOG=1", "PACKER_CACHE_DIR=/tmp/packer", command].join(" "))
                                                                    }
                        }
                    }
                }
            }
            stage("Deploy") {
                steps {
                    script {
                        sh String.format("rsync --archive --verbose --backup output/ %s",
                                         ("rsync://archive.intr/images/" + (env.BRANCH_NAME == "master" ? "jenkins-production" : "jenkins-development") + "/"))
                        (args.postDeploy ?: { return true })()
                    }
                }
            }
        }
        post {
            always {
                sendNotifications currentBuild.result
                cleanWs()
            }
        }
    }
}


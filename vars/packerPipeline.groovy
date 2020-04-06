def call(Map args = [:]) {
    Boolean administration = args.administration ? args.administration : false
    pipeline {
        agent { label "master" }
        environment {
            PACKER_LOG = "1"
            PACKER_CACHE_DIR = "/tmp/packer"
        }
        parameters {
            booleanParam(name: "DEPLOY",
                         defaultValue: env.BRANCH_NAME == "master" ? true : false,
                         description: "If checked deploy to production otherwise to staging.")
        }
        options {
            buildDiscarder(logRotator(numToKeepStr: "10", artifactNumToKeepStr: "10"))
            disableConcurrentBuilds()
        }
        stages {
            stage("Build virtual machine") {
                steps {
                    warnError("Failed to build virtual machine") {
                        packerBuildImages (
                            distribution: args.distribution,
                            release: args.release,
                            id: args.id,
                            administration: administration,
                            deploy: params.DEPLOY,
                            nodeLabels: ["kvm-template-builder"]
                        )
                    }
                }
            }
        }
        post {
            success { cleanWs() }
            always {
                sendNotifications currentBuild.result
            }
        }
    }
}

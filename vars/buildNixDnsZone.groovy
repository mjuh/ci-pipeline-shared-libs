def call(Map args = [:]) {
    pipeline {
        agent { label "master" }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
        }
        stages {
            stage("test") {
                steps {
                    ansiColor("xterm") {
                        script {
                            sh (nix.shell(run: "nix build --print-build-logs"))
                            sh (nix.shell(run: "deploy --dry-activate . -- --print-build-logs"))
                            result =
                                (sh (script: "nix-shell --run 'nix path-info'",
                                     returnStdout: true)).trim()
                            sh "cp ${result} bind.zone"
                            archiveArtifacts artifacts: "bind.zone"
                            sh """
                               if curl --fail --output bind.1.zone https://jenkins.intr/job/net/job/net%2F${GITLAB_PROJECT_NAME}/job/master/lastBuild/artifact/bind.zone
                               then
                                   (
                                       set +e
                                       diff -u bind.1.zone bind.zone > bind.zone.diff
                                       exit 0
                                   )
                               fi
                               """
                            if (fileExists("bind.zone.diff")) {
                                archiveArtifacts artifacts: "bind.zone.diff"
                            }
                        }
                    }
                }
            }
            stage("deploy") {
                when { branch "master" }
                steps {
                    ansiColor("xterm") {
                        script {
                            sh (nix.shell(run: "deploy . -- --print-build-logs"))
                            (args.postDeploy ?: { return true })()
                        }
                    }
                }
            }
        }
        post {
            always {
                sh "rm -f bind.1.zone bind.zone bind.zone.diff"
            }
        }
    }
}

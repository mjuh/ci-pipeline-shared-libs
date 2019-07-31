def call() {
    pipeline {
        agent { label 'master' }
        environment {
            PROJECT_NAME = gitRemoteOrigin.getProject()
            GROUP_NAME = gitRemoteOrigin.getGroup()
        }
        options { gitLabConnection(Constants.gitLabConnection) }
        stages {
            stage('Build Nix overlay') {
                steps {
                    gitlabCommitStatus(STAGE_NAME) {
                        sh '. /var/jenkins_home/.nix-profile/etc/profile.d/nix.sh && ' +
                            'nix-build build.nix --cores 8 -A nixpkgsUnstable'
                    }
                }
            }
        }
        post {
            success { cleanWs() }
            failure { notifySlack "Build failled: ${JOB_NAME} [<${RUN_DISPLAY_URL}|${BUILD_NUMBER}>]", "red" }
        }
    }
}

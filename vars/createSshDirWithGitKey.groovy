def call(Map args) {
    def gitCredId = args.gitCredId ?: Constants.gitCredId
    def gitHost = args.gitHost ?: Constants.gitHost
    def localHomedir = args.localHomedir ?: env.HOME
    def sshDir = args.dir ?: '.ssh'
    def sshWrapperFilename = args.sshWrapperFilename ?: 'wrap-ssh4git.sh'

    sh "mkdir -p -m 700 ${sshDir}"

    withCredentials([[$class: 'SSHUserPrivateKeyBinding', credentialsId: gitCredId,
                      usernameVariable: 'USERNAME', keyFileVariable: 'KEY_FILE']]) {
        dir(sshDir) {
                writeFile(
                    file: 'config',
                    text: """
                        Host ${gitHost}
                        User $USERNAME
                        HostName ${gitHost}
                        IdentityFile ${localHomedir}/${sshDir}/git_repos_deploy_key
                    """
                );

                writeFile(
                    file: sshWrapperFilename,
                    text: """
                        #!/bin/sh
                        /usr/bin/env ssh -o 'StrictHostKeyChecking=no' -i '${localHomedir}/${sshDir}/git_repos_deploy_key' \$1 \$2
                    """
                );


                sh """
                    chmod +x ${sshWrapperFilename}
                    cp $KEY_FILE git_repos_deploy_key
                    chmod 400 git_repos_deploy_key
                """
        }
    }
}

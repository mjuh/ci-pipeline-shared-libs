def call(Map args = [:]) {
    def gitCredId = args.gitCredId ?: Constants.gitCredId
    def gitHost = args.gitHost ?: Constants.gitHost
    def inConfigDir = args.inConfigDir ?: env.HOME + '/.ssh'
    def sshDir = args.dir ?: env.HOME + '/.ssh'
    def sshWrapperFilename = args.sshWrapperFilename ?: 'wrap-ssh4git.sh'

    sh "mkdir -p -m 700 ${sshDir}"

    withCredentials([[$class: 'SSHUserPrivateKeyBinding', credentialsId: gitCredId,
                      usernameVariable: 'USERNAME', keyFileVariable: 'KEY_FILE']]) {
        dir(sshDir) {
                writeFile(
                    file: 'config',
                    text: """
                            Host ${gitHost}
                            User git
                            HostName ${gitHost}
                            IdentityFile ${inConfigDir}/ssh_deploy_key

                            Host github.com
                            User git
                            IdentityFile ${inConfigDir}/ssh_deploy_key
                    """.stripMargin().stripIndent()
                );

                writeFile(
                    file: sshWrapperFilename,
                    text: """
                            #!/bin/sh
                            /usr/bin/env ssh -o 'StrictHostKeyChecking=no' -i '${inConfigDir}/ssh_deploy_key' \$1 \$2
                    """.stripMargin().stripIndent()
                );


                sh """
                    chmod +x ${sshWrapperFilename}
                    test -f ssh_deploy_key && chmod +w ssh_deploy_key
                    cp $KEY_FILE ssh_deploy_key
                    chmod 600 ssh_deploy_key
                    /usr/bin/env ssh-keygen -y -f ssh_deploy_key > ssh_deploy_key.pub
                """
        }
    }
}

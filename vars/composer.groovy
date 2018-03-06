def call(Map args) {
    def cmd = args.cmd ?: 'install'
    def registry = args.registry ?: Constants.dockerRegistryHost
    def dockerCredId = args.credentialsId ?: Constants.dockerRegistryCredId
    def composerNs = args.composerDockerNamespace ?: Constants.composerDockerNamespace
    def composerImage = args.composerDockerImage ?: Constants.composerDockerImage
    def composerTag = args.composerDockerTag ?: Constants.composerDockerTag
    def phpVersion = args.phpVersion ?: 'php56'
    def srcDir = args.srcDir ?: 'src'
    def jenkinsHomeOnHost = new JenkinsContainer().getMountByDestination(env.HOME).Source
    def jenkinsHomeInContainer = '/home/jenkins'
    def workspaceOnHost = jenkinsHomeOnHost + (env.WORKSPACE - env.HOME)
    def uid = sh(returnStdout: true, script: 'id -u').trim()

    sh "mkdir -p $HOME/composer/home"
    createSshDirWithGitKey(dir: env.WORKSPACE + '/jenkins_home/.ssh',
                           inConfigDir: jenkinsHomeInContainer,
                           sshWrapperFilename: 'ssh_wrapper.sh')
    writeFile(file: 'passwd', text: "jenkins:x:${uid}:${uid}:,,,,:${jenkinsHomeInContainer}:/bin/sh\n")
    writeFile(file: 'group', text: "jenkins:x:${uid}:jenkins\n")
    sh "cp -pR $WORKSPACE/${srcDir} $WORKSPACE/build"

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: dockerCredId,
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
        sh "docker login -u $USERNAME -p $PASSWORD ${registry}"
        sh """
            docker run --rm --name composer                                     \
            --user ${uid}:${uid}                                                \
            -e 'PHP_VERSION=${phpVersion}'                                      \
            -e 'COMPOSER_HOME=/composer/home'                                   \
            -e 'GIT_SSH=${jenkinsHomeInContainer}/.ssh/ssh_wrapper.sh'          \
            -v ${workspaceOnHost}/passwd:/etc/passwd:ro                         \
            -v ${workspaceOnHost}/group:/etc/group:ro                           \
            -v ${workspaceOnHost}/jenkins_home:${jenkinsHomeInContainer}        \
            -v ${jenkinsHomeOnHost}/composer:/composer                          \
            -v ${workspaceOnHost}/build:/app                                    \
            ${registry}/${composerNs}/${composerImage}:${composerTag} ${cmd}
        """
    }
}

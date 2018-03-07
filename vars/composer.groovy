def call(Map args = [:]) {
    def cmd = args.cmd ?: 'install'
    def composerImage = args.composerDockerImage ?: Constants.composerDockerImage
    def composerTag = args.composerTag ?: Constants.composerDockerTag
    def phpVersion = args.phpVersion ?: 'php56'
    def srcDir = args.srcDir ?: 'src'
    def jenkinsContainer = new JenkinsContainer()
    def homeOnHost = jenkinsContainer.getHostPath(env.HOME)
    def workspaceOnHost = jenkinsContainer.getHostPath(env.WORKSPACE)
    def uid = jenkinsContainer.getUid()
    def jenkinsHomeInComposerContainer = '/home/jenkins'

    echo 'Copying src/ to build/'
    sh "cp -a $WORKSPACE/${srcDir}/. $WORKSPACE/build"

    echo 'Preparing volumes content for Docker container ...'

    echo '... creating composer/home'
    sh "mkdir -p $HOME/composer/home"

    echo '... creating passwd and group files with single user `jenkins`'
    writeFile(file: 'passwd', text: "jenkins:x:${uid}:${uid}:,,,,:${jenkinsHomeInComposerContainer}:/bin/sh\n")
    writeFile(file: 'group', text: "jenkins:x:${uid}:jenkins\n")

    echo '... creating .ssh with config, wrapper and key needed for `git clone`'
    createSshDirWithGitKey(dir: env.WORKSPACE + '/jenkins_home/.ssh',
                           inConfigDir: jenkinsHomeInComposerContainer,
                           sshWrapperFilename: 'ssh_wrapper.sh')

    echo 'Running Docker container'
    dockerRun(volumes: ["${workspaceOnHost}/passwd": '/etc/passwd',
                        "${workspaceOnHost}/group": '/etc/group',
                        "${workspaceOnHost}/jenkins_home": jenkinsHomeInComposerContainer,
                        "${homeOnHost}/composer": '/composer',
                        "${workspaceOnHost}/build": '/app'],
              env: [PHP_VERSION: phpVersion,
                    COMPOSER_HOME: '/composer/home',
                    GIT_SSH: "${jenkinsHomeInComposerContainer}/.ssh/ssh_wrapper.sh"],
              image: "${composerImage}:${composerTag}",
              name: "composer-${env.BUILD_TAG}",
              cmd: cmd)
}

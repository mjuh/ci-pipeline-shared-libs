def call(Map args) {
    def cmd = args.cmd ?: 'install'
    def registry = args.registry ?: Constants.dockerRegistryHost
    def composerNs = args.composerDockerNamespace ?: Constants.composerDockerNamespace
    def composerImage = args.composerDockerImage ?: Constants.composerDockerImage
    def composerTag = args.composerDockerTag ?: Constants.composerDockerTag
    def phpVersion = args.phpVersion ?: 'php56'
    def srcDir = args.srcDir ?: 'src'
    def jenkinsHomeOnHost = new JenkinsContainer().getMountByDestination(env.HOME).Source
    def jenkinsHomeInContainer = '/home/jenkins'
    def workspaceOnHost = jenkinsHomeOnHost + (env.WORKSPACE - env.HOME)
    def uid = sh(returnStdout: true, script: 'id -u').trim()

    echo 'Copying src/ to build/'
    sh "cp -a $WORKSPACE/${srcDir}/. $WORKSPACE/build"

    echo 'Preparing volumes content for Docker container ...'

    echo '... creating composer/home'
    sh "mkdir -p $HOME/composer/home"

    echo '... creating passwd and group files with single user `jenkins`'
    writeFile(file: 'passwd', text: "jenkins:x:${uid}:${uid}:,,,,:${jenkinsHomeInContainer}:/bin/sh\n")
    writeFile(file: 'group', text: "jenkins:x:${uid}:jenkins\n")

    echo '... creating .ssh with config, wrapper and key needed for `git clone`'
    createSshDirWithGitKey(dir: env.WORKSPACE + '/jenkins_home/.ssh',
                           inConfigDir: jenkinsHomeInContainer,
                           sshWrapperFilename: 'ssh_wrapper.sh')

    echo 'Running Docker container'
    dockerRun(volumes: ["${workspaceOnHost}/passwd": '/etc/passwd',
                        "${workspaceOnHost}/group": '/etc/group',
                        "${workspaceOnHost}/jenkins_home": jenkinsHomeInContainer,
                        "${jenkinsHomeOnHost}/composer": '/composer',
                        "${workspaceOnHost}/build": '/app'],
              env: [PHP_VERSION: phpVersion,
                    COMPOSER_HOME: '/composer/home',
                    GIT_SSH: "${jenkinsHomeInContainer}/.ssh/ssh_wrapper.sh"],
              image: "${registry}/${composerNs}/${composerImage}:${composerTag}",
              name: "composer-${env.BUILD_TAG}",
              cmd: cmd)
}

def call(Map args = [:]) {
    def cmd = args.cmd ?: 'install'
    def composerImage = args.composerDockerImage ?: Constants.composerDockerImage
    def composerTag = args.composerTag ?: Constants.composerDockerTag
    def phpVersion = args.phpVersion ?: 'php56'
    def srcDir = args.srcDir ?: 'src'
    def passwdHostPath = env.WORKSPACE + '/passwd'
    def groupHostPath = env.WORKSPACE + '/group'
    def jenkinsHomeHostPath = env.WORKSPACE + '/jenkins_home'
    def composerHostPath = env.HOME + '/composer'
    def buildHostPath = env.WORKSPACE + '/build'
    def uid = 109
    def jenkinsHomeInComposerContainer = '/home/jenkins'

    echo 'Copying src/ to build/'
    sh "cp -a $WORKSPACE/${srcDir}/. $WORKSPACE/build"

    echo 'Preparing volumes content for Docker container ...'

    echo '... creating composer/home'
    sh "mkdir -p $HOME/composer/home"

    echo '... creating passwd and group files with single user `jenkins`'
    createPasswdFiles(users: [jenkins: [uid: uid]])

    echo '... creating .ssh with config, wrapper and key needed for `git clone`'

    createSshDirWithGitKey(dir: env.WORKSPACE + '/jenkins_home/.ssh',
                           inConfigDir: jenkinsHomeInComposerContainer + '/.ssh',
                           sshWrapperFilename: 'ssh_wrapper.sh')

    echo 'Running Docker container'
    dockerRun(volumes: [(passwdHostPath): '/etc/passwd',
                        (groupHostPath): '/etc/group',
                        (jenkinsHomeHostPath): jenkinsHomeInComposerContainer,
                        (composerHostPath): '/composer',
                        (buildHostPath): '/app'],
              env: [PHP_VERSION: phpVersion,
                    COMPOSER_HOME: '/composer/home',
                    GIT_SSH: "${jenkinsHomeInComposerContainer}/.ssh/ssh_wrapper.sh"],
              image: "${composerImage}:${composerTag}",
              name: "composer-${env.BUILD_TAG}",
              cmd: cmd)
    echo 'Removing composer.lock for next build'
    sh "rm -f $WORKSPACE/build/composer.lock"
}

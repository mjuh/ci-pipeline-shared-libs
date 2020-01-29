def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
        assert args.currentProjectBranch : "No current project branch provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"
    def overlaybranch = args.overlaybranch ?: "master"

    createSshDirWithGitKey()

    def BUILD_CMD_TEMPLATE = ["nix-build", "--tarball-ttl", "10",
                              "--argstr", "ref", overlaybranch,
                              "--show-trace"]
    def BUILD_CMD = BUILD_CMD_TEMPLATE.join(" ")
    def BUILD_CMD_DEBUG = (BUILD_CMD_TEMPLATE + [
            "--arg", "debug", "true", "--out-link", "debug"
        ]).join(" ")

    [BUILD_CMD, BUILD_CMD_DEBUG].each{
        print("Invoking ${it}")
        nixSh cmd: it
    }

    def path = sh(returnStdout: true, script: 'readlink result').trim()

    def repoTag = nixRepoTag overlaybranch: overlaybranch,
    currentProjectBranch: args.currentProjectBranch

    def fqImageName = "${Constants.dockerRegistryHost}/${imageName}:$repoTag"
    new DockerImageTarball(imageName: fqImageName, path: path)
}

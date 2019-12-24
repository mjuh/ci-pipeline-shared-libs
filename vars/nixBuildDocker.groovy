def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
        assert args.currentProjectBranch : "No current project branch provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"
    def overlaybranch = args.overlaybranch ?: "master"

    createSshDirWithGitKey()

    nixSh cmd: "nix-build --tarball-ttl 10 --argstr ref $overlaybranch --show-trace"
    def path = sh(returnStdout: true, script: 'readlink result').trim()
    if (overlaybranch == args.currentProjectBranch) {
        repoTag = overlaybranch
    } else {
        repoTag = overlaybranch + "_" + args.currentProjectBranch
    }

    def fqImageName = "${Constants.dockerRegistryHost}/${imageName}:$repoTag"
    new DockerImageTarball(imageName: fqImageName, path: path)
}

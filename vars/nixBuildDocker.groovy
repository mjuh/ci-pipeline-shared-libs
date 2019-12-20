def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
        assert args.overlaybranch : "No overlay branch provided"
        assert args.currentProjectBranch : "No current project branch provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"

    createSshDirWithGitKey()

    nixSh cmd: "nix-build --tarball-ttl 10 --argstr ref $args.overlaybranch --show-trace"
    def path = sh(returnStdout: true, script: 'readlink result').trim()
    if (args.overlaybranch == args.currentProjectBranch) {
        repoTag = args.overlaybranch
    } else {
        repoTag = args.overlaybranch + "_" + args.currentProjectBranch
    }

    def fqImageName = "${Constants.dockerRegistryHost}/${imageName}:$repoTag"
    new DockerImageTarball(imageName: fqImageName, path: path)
}

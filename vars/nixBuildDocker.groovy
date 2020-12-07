def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }
    Boolean debug = args.debug ?: false
    List<String> nixArgs = args.nixArgs ?: [""]
    def nixFile = args.nixFile ?: 'default.nix'
    String saveResult = args.saveResult == false ? "" : "--out-link result/${env.JOB_NAME}/docker-${env.BUILD_NUMBER}"
    String buildCmd = ""
    if (args.overlay) {
        buildCmd = (
            ["nix-build",
             "--tarball-ttl 10",
             "--argstr overlayUrl $args.overlay.url",
             "--argstr overlayRef $args.overlay.branch",
             "--show-trace"] + saveResult + nixFile
        ).join(" ")
    } else {
        buildCmd = [
            "nix-build",
            "--out-link result/${env.JOB_NAME}/docker-${env.BUILD_NUMBER}",
            "--tarball-ttl 10",
            "--show-trace", "$nixFile"].join(" ")
    }
    String imageName = args.imageName ?: "${args.namespace}/${args.name}"
    String tag = args.tag ?: GIT_BRANCH

    createSshDirWithGitKey()

    nix.nixBuildWrap()

    new DockerImageTarball(
        imageName: "${Constants.dockerRegistryHost}/${imageName}:$tag",
        path: (
            sh (script: ['PATH=bin:$PATH', buildCmd, nixArgs.join(" ")].join(" "),
                returnStdout: true).trim()
        )
    )
}

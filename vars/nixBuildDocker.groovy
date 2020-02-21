def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }
    Boolean debug = args.debug ?: false
    List<String> nixArgs = args.nixArgs ?: [""]
    String overlaybranch = args?.overlay?.branch ?: "master"
    String buildCmd = "nix-build --out-link result/${env.JOB_NAME}/${env.BUILD_NUMBER} --tarball-ttl 10 --argstr ref $overlaybranch --show-trace"
    String imageName = args.imageName ?: "${args.namespace}/${args.name}"
    String tag = args.tag ?: GIT_BRANCH

    createSshDirWithGitKey()

    new DockerImageTarball(imageName: "${Constants.dockerRegistryHost}/${imageName}:$tag",
                           path: (sh (script: "$buildCmd ${nixArgs.join(' ')}", returnStdout: true).trim()))
}

def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"

    createSshDirWithGitKey()

    nixSh cmd: 'nix-build --tarball-ttl 10 --show-trace'

    def image = new DockerImageTarball('result')
    def fqImageName = "${Constants.dockerRegistryHost}/${imageName}:${args.tag ?: image.getTag()}"
    if (fqImageName != image.imageName()) {
        image.setImageName(fqImageName)
    }
    image
}
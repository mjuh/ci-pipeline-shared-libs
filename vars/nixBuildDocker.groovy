def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"

    createSshDirWithGitKey()

    sh 'nix-build --tarball-ttl 10 --show-trace'
    def path = sh(returnStdout: true, script: 'readlink result').trim()
    sh 'tar xzf result manifest.json'
    def repoTag = readJSON(file: 'manifest.json')[0].RepoTags[0]
    def fqImageName = "${Constants.dockerRegistryHost}/${imageName}:${args.tag ?: repoTag.split(':')[-1]}"
    new DockerImageTarball(imageName: fqImageName, path: path)
}
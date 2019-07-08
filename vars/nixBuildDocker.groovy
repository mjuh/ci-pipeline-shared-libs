def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"
    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    createSshDirWithGitKey()

    docker.withRegistry(registryUrl, credentialsId) {
        sh '. /var/jenkins_home/.nix-profile/etc/profile.d/nix.sh && ' +
           'docker load --input $(nix-build --cores 8 --tarball-ttl 10 --show-trace)'
        sh 'tar xzf result manifest.json'
        def repoTag = readJSON(file: 'manifest.json')[0].RepoTags[0]
        def fqImageName = "${Constants.dockerRegistryHost}/${imageName}:${args.tag ?: repoTag.split(':')[-1]}"
        println("Nix produced image '${repoTag}' will be tagged as '${fqImageName}'")
        sh "docker tag ${repoTag} ${fqImageName}"
        docker.image(fqImageName)
    }
}
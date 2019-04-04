def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"
    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    docker.withRegistry(registryUrl, credentialsId) {
        sh '. /var/jenkins_home/.nix-profile/etc/profile.d/nix.sh && docker load --input \$(nix-build --cores 8 default.nix --show-trace)'
        sh 'tar xzf result manifest.json'
        def repoTag = readJSON(file: 'manifest.json')[0].repoTags[0]
        println('Nix produced image: ' + repoTag)
        def image = docker.image(repoTag)
        image.tag("${imageName}:${tag}")
        image
    }
}

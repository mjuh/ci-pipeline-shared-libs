def call(Map args = [:]) {
    assert args.image : "No image provided"

    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    docker.withRegistry(registryUrl, credentialsId) {
        args.image.push()
    }
}
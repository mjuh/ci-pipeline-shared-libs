def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.name : "No image name provided"
        assert args.namespace : "No namespace provided"
    }

    def imageName = args.imageName ?: "${args.namespace}/${args.name}"
    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def dockerfile = args.dockerfile ?: "Dockerfile"
    def dockerfileDir = args.dockerfileDir ?: "."
    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    docker.withRegistry(registryUrl, credentialsId) {
        docker.build("${imageName}:${tag}", "-f ${dockerfile} ${dockerfileDir}")
    }
}

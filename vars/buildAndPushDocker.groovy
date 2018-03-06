def call(Map args) {

    assert args.image : "No image name provided"
    assert args.namespace : "No namespace provided"

    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def dockerfile = args.dockerfile ?: "Dockerfile"
    def dockerfileDir = args.dockerfileDir ?: "."
    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    docker.withRegistry(registryUrl, credentialsId) {
        def dockerImage = docker.build("${args.namespace}/${args.image}:${tag}", "-f ${dockerfile} ${dockerfileDir}")
        echo dockerImage
        dockerImage.push()
    }
}

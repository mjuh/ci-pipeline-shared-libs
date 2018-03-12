def call(Map args = [:]) {

    assert args.image : "No image name provided"
    assert args.namespace : "No namespace provided"

    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def dockerfile = args.dockerfile ?: "Dockerfile"
    def dockerfileDir = args.dockerfileDir ?: "."
    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    docker.withRegistry(registryUrl, credentialsId) {
        def dockerImage = docker.build("${args.namespace}/${args.image}:${tag}", "-f ${dockerfile} ${dockerfileDir}")
        if(args.structureTestConfig) {
            assert fileExists(args.structureTestConfig) : "args.structureTestConfig does not exist"
            containerStructureTest(namespace: args.namespace, image: args.image, tag: tag)
        }
        dockerImage.push()
    }
}

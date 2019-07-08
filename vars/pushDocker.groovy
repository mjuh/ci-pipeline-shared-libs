def call(Map args = [:]) {
    assert args.image : "No image provided"

    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId

    docker.withRegistry(registryUrl, credentialsId) {
        args.image.push()
        args.image.push('latest')
        if(env.GIT_COMMIT) {
            args.image.push(env.GIT_COMMIT[0..7])
        }
        if(env.BRANCH_NAME) {
            args.image.push(env.BRANCH_NAME)
        }
    }
}
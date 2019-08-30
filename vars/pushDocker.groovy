def call(Map args = [:]) {
    assert args.image : "No image provided"

    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    def extraTags = ['latest']

    if (env.GIT_COMMIT) {
        extraTags += env.GIT_COMMIT[0..7]
    }
    if (env.BRANCH_NAME) {
        extraTags += env.BRANCH_NAME
    }

    if(args.image.metaClass.respondsTo(args.image, 'push')) {
        docker.withRegistry(registryUrl, credentialsId) {
            args.image.push()
            extraTags.each {
                args.image.push(it)
            }
        }
    } else {
        String baseName = args.image.imageName.split(':')[0..-2].join()
        List tags = [args.image.imageName.split(':')[-1]] + extraTags
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                          usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
            String user = env.REGISTRY_USERNAME
            String password = env.REGISTRY_PASSWORD
            tags.each { tag ->
                nixSh cmd: "skopeo copy " +
                           "--dest-creds=${user}:${password} --dest-tls-verify=false " +
                           "docker-archive:${args.image.path} docker://${baseName}:${origTag}",
                      pkgs: ['skopeo']
            }
        }
    }
}
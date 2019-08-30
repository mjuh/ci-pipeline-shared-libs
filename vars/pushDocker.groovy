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
        def baseName = args.image.split(':')[0..-2].join()
        def origTag = args.image.split(':')[-1]
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                          usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
            (origTag + extraTags).each { tag ->
                nixSh cmd: "skopeo copy --dest-creds=${env.REGISTRY_USERNAME}:${env.REGISTRY_PASSWORD} " +
                           "--dest-tls-verify=false " +
                           "docker-archive:${args.image.path} " +
                           "docker://docker-registry.intr/webservices/${baseName}:${tag}",
                      pkgs: ['skopeo']
            }
        }
    }
}
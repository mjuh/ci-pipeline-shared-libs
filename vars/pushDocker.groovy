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

    if(image.metaClass.respondsTo(args.image, 'push')) {
        docker.withRegistry(registryUrl, credentialsId) {
            args.image.push()
            extraTags.each {
                args.image.push(it)
            }
        }
    } else {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                          usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
            def nixSh = new NixShell(pkgs: ['skopeo'])
            (args.image.getTag() + extraTags).each { tag ->
                nixSh.run "skopeo copy --dest-creds=${env.REGISTRY_USERNAME}:${env.REGISTRY_PASSWORD} " +
                        "--dest-tls-verify=false " +
                        "docker-archive:${args.image.path} " +
                        "docker://docker-registry.intr/webservices/ssh-guest-room:${tag}"
            }
        }
    }
}
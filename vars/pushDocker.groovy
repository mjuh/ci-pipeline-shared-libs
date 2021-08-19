def call(Map args = [:]) {
    assert args.image : "No image provided"

    List<String> extraTags = args.extraTags ?: ['latest']
    extraTags += gitHeadShort()

    lock("docker-registry") {
        if(args.image.metaClass.respondsTo(args.image, 'push')) {
            String registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
            String credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
            docker.withRegistry(registryUrl, credentialsId) {
                args.image.push()
                extraTags.each {
                    args.image.push(it)
                }
            }
        } else {
            String originTag = args.tag ?: env.BRANCH_NAME
            String baseName = args.image.imageName.split(':')[0..-2].join()
            List<String> commands = []
            // Don't use docker load, because it cannot load and tag at the same time.
            commands += "skopeo copy docker-archive:${args.image.path} docker-daemon:${baseName}:${originTag} --insecure-policy"
            commands += "docker push ${baseName}:${originTag}"
            (([args.image.imageName.split(':')[-1]] + extraTags).unique()).each { tag ->
                if (originTag != tag) {
                    commands += "docker tag ${baseName}:${originTag} ${baseName}:${tag}"
                    commands += "docker push ${baseName}:${tag}"
                }
            }
            sh (commands.join("; "))
        }
    }
}

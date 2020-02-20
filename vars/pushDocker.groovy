def call(Map args = [:]) {
    assert args.image : "No image provided"

    List<String> extraTags = args.extraTags ?: ['latest']
    if (env.GIT_COMMIT) {
        extraTags += env.GIT_COMMIT[0..7]
    }

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
        String tagload = (sh (script: "docker load --input ${args.image.path}'", returnStdout: true)).trim().split(" ").last()
        List<String> commands = []
        commands += "docker tag $tagload ${baseName}:${originTag}"
        commands += "docker push ${baseName}:${originTag}"
        (([args.image.imageName.split(':')[-1]] + extraTags).unique()).each { tag ->
            commands += "docker tag ${baseName}:${originTag} ${baseName}:${tag}"
            commands += "docker push ${baseName}:${tag}"
        }
        sh (commands.join("; "))
    }
}

def call(Map args = [:]) {
    assert args.image : "No image provided"

    String registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    String credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    List<String> extraTags = ['latest']
    String pushDocker = args.tag ?: env.BRANCH_NAME

    boolean pushToBranchName = args.pushToBranchName ?: false

    if (env.GIT_COMMIT) {
        extraTags += env.GIT_COMMIT[0..7]
        extraTags += pushDocker
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
        tags.unique()
        sh "skopeo copy docker-archive:${args.image.path} docker-daemon:${baseName}:${pushDocker}"
        tags.each { tag ->
            sh (["docker tag ${baseName}:${pushDocker} ${baseName}:${tag}",
                 "docker push ${baseName}:${tag}"].join("; "))
        }
    }
}

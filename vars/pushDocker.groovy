def call(Map args = [:]) {
    assert args.image : "No image provided"

    def registryUrl = args.registryUrl ?: "https://" + Constants.dockerRegistryHost
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    def extraTags = ['latest']

    if (env.GIT_COMMIT) {
        extraTags += env.GIT_COMMIT[0..7]
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
        String repoTag = nixRepoTag (overlaybranch: args.overlaybranch,
                                     currentProjectBranch: args.currentProjectBranch)
        String baseName = args.image.imageName.split(':')[0..-2].join()
        List tags = [args.image.imageName.split(':')[-1]] + extraTags
        tags.unique()
        sh "skopeo copy docker-archive:${args.image.path} docker-daemon:${baseName}:${repoTag}"
        tags.each { tag ->
            sh "docker tag ${baseName}:${repoTag} ${baseName}:${tag}"
            sh "docker push ${baseName}:${tag}"
        }
    }
}

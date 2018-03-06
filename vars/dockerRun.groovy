def call(Map args =[:]) {
    assert args.image : "No image provided"

    def image = args.image
    def uid = args.uid ?: sh(returnStdout: true, script: 'id -u').trim()
    def dockerCredId = args.credentialsId ?: Constants.dockerRegistryCredId
    def registry = args.registry ?: Constants.dockerRegistryHost
    def dockerArgs = "--user ${uid}:${uid} "

    if(args.tag && !image.endsWith(args.tag)) {
        image = image.split(':')[0] + ':' + args.tag
    }

    if(!args.persist) {
        dockerArgs += "--rm "
    }

    if(args.name) {
        dockerArgs += "--name ${args.name} "
    }

    args.volumes.each { k, v ->
        dockerArgs += "-v ${k}:${v} "
    }

    args.env.each {k, v ->
        dockerArgs += "-e '${k}=${v}' "
    }

    def dockerCmd = "docker run ${dockerArgs} ${image}"

    if(args.cmd) {
        dockerCmd += " ${args.cmd}"
    }

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: dockerCredId,
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
        sh """
            docker login -u $USERNAME -p $PASSWORD ${registry}
            ${dockerCmd}
        """
    }
}

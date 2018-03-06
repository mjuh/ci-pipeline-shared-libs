def call(Map args) {
    assert args.image : "No image provided"

    def uid = args.uid ?: sh(returnStdout: true, script: 'id -u').trim()
    def dockerCredId = args.credentialsId ?: Constants.dockerRegistryCredId
    def dockerArgs = "--user ${uid}:${uid} "

    if(!args.persist) {
        dockerArgs += "--rm "
    }

    if(args.name) {
        dockerArgs += "--name ${args.name}"
    }

    args.volumes.each { k, v ->
        dockerArgs += "-v ${k}:${v} "
    }

    args.env.each {k, v ->
        dockerArgs += "-e '${k}=${v}' "
    }

    def dockerCmd = "docker run ${dockerArgs} ${args.image}"

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

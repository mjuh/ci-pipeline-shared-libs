def call(Map args) {
    assert args.image : "No image provided"

    def uid = args.uid ?: sh(returnStdout: true, script: 'id -u').trim()
    def dockerCredId = args.credentialsId ?: Constants.dockerRegistryCredId
    def name = args.name ?: args.image.split(":")​[0]​.split("/")[-1]​ + env.BUILD_TAG
    def dockerArgs = "--name ${name} --user ${uid}:${uid} "

    if(!args.persist) {
        dockerArgs += "--rm "
    }

    args.volumes.each { k, v ->
        dockerArgs += "-v ${k}:${v} "
    }
    args.env.each {k, v ->
        dockerArgs += "-e '${k}=${v}' "
    }

    def dockerCmd = "docker run ${dockerArgs} ${image}"
    if(args.cmd) {
        dockerCmd += " ${cmd}"
    }

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: dockerCredId,
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
        sh """
            docker login -u $USERNAME -p $PASSWORD ${registry}
            ${dockerCmd}
        """
    }
}

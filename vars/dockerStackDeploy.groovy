import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def call(Map args) {
    assert args.stack : "No stack name provided"

    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    def prodService = jsonParse(
        sh(returnStdout: true,
           script: "docker service inspect ${args.stack}_${args.service} 2>/dev/null || true").trim()
    )
    def service = prodService ? args.service : ''
    def ns = args.namespace ?: args.stack
    def image = args.image ?: args.service
    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def registry = args.registry ?: Constants.dockerRegistryHost

    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                    usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
        sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
        if(service) {
            echo "Service ${args.service} exists, info:\n${prodService}"
            sh "docker service update --with-registry-auth --force --image ${registry}/${ns}/${image}:${tag} ${args.stack}_${service}"
        } else {
            def stacksDir = "${env.HOME}/${Constants.dockerStacksDeployDir}"
            dir(stacksDir) {
                git(url: Constants.dockerStacksGitRepoUrl,
                    credentialsId: Constants.gitCredId)
            }
            sh "docker stack deploy --with-registry-auth -c ${stacksDir}/${args.stack}.yml ${args.stack}"
        }
    }
}

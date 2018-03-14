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

    dir("${env.HOME}/${Constants.dockerStacksDeployDir}") {
        git(url: Constants.dockerStacksGitRepoUrl, credentialsId: Constants.gitCredId)

        def stackConfigFile = "${args.stack}.yml"
        def stackDeclaration = readYaml(file: stackConfigFile)

        withCredentials([[$class          : 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                          usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
            sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
            if (service) {
                def serviceDeclaration = stackDeclaration.services."${service}"
                echo """
                    Service ${args.service} exists,
                    name: ${prodService.Spec.Name}
                    image: ${prodService.Spec.TaskTemplate.ContainerSpec.Image}
                    mode: ${prodService.Spec.Mode}
                """.stripMargin().stripIndent()
                def cmd = 'docker service update --detach=false --with-registry-auth --force '
                if(serviceDeclaration.deploy.replicas) {
                    cmd += "--replicas ${serviceDeclaration.deploy.replicas} "
                }
                cmd += "--image ${registry}/${ns}/${image}:${tag} ${args.stack}_${service}"
                sh cmd
            } else {
                sh "docker stack deploy --with-registry-auth -c ${stackConfigFile} ${args.stack}"
            }
        }
    }
}

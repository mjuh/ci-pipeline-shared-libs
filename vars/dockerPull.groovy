@NonCPS
def getNodeNames(String label) {
    jenkins.model.Jenkins.instance.nodes
            .findAll { node -> node.labelString == label }
            .collect { node -> node.name }
}

def call(Map args = [:]) {
    assert args.image : "No Image provided"
    def imageName = args.image.imageName()
    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    def registry = args.registry ?: Constants.dockerRegistryHost
    def nodeLabel = args.nodeLabel ?: Constants.productionNodeLabel
    imageName = "${registry}/" + imageName - "${registry}/"

    def nodes = [:]
    def names = getNodeNames(nodeLabel)
    for (int i=0; i<names.size(); ++i) {
        def nodeName = names[i];
        nodes[nodeName] = {
            node(nodeName) {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                                  usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
                    sh """
                        docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}
                        docker pull ${imageName}
                    """
                }
            }
        }
    }
    parallel nodes
}
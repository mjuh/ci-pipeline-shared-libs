@NonCPS
def getNodeNames(List<String> labels) {
    jenkins.model.Jenkins.instance.nodes
            .findAll { node -> labels.contains(node.labelString) }
            .collect { node -> node.name }
}

def call(Map args = [:]) {
    assert args.cmd: "No command provided"
    assert args.nodeLabels : "No node labels provided"
    assert args.nodeLabels instanceof List<String> : "Node labels should be a list of strings"

    def nodes = [:]
    def names = getNodeNames(args.nodeLabels)
    for (int i=0; i<names.size(); ++i) {
        def nodeName = names[i];
        nodes[nodeName] = {
            node(nodeName) {
                sh args.cmd
            }
        }
    }
    parallel nodes
}

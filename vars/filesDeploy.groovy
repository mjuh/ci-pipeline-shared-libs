@NonCPS
def getNodeNames(List<String> labels) {
    jenkins.model.Jenkins.instance.nodes
            .findAll { node -> labels.contains(node.labelString) }
            .collect { node -> node.name }
}

def call(Map args = [:]) {
    assert args.srcPath : "No source path provided"
    assert args.dstPath: "No destinaion path provided"
    assert args.nodeLabels : "No node labels provided"
    assert args.nodeLabels instanceof List<String> : "Node labels should be a list of strings"
    stashName = 'transfer'

    dir(args.srcPath){
        stash(name: stashName) 
    }

    def nodes = [:]
    def names = getNodeNames(args.nodeLabels)
    for (int i=0; i<names.size(); ++i) {
        def nodeName = names[i];
        nodes[nodeName] = {
            node(nodeName) {
                if(args.preDeployCmd) {
                    sh args.preDeployCmd
                }
                dir(args.dstPath) {
                    unstash(name: stashName)
                }
                if(args.postDeployCmd) {
                    sh args.postDeployCmd
                }
            }
        }
    }
    parallel nodes
}

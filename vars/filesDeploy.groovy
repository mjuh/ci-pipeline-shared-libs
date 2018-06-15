@NonCPS
def getNodeNames(String label) {
    jenkins.model.Jenkins.instance.nodes
            .findAll { node -> node.labelString == label }
            .collect { node -> node.name }
}

def call(Map args = [:]) {
    assert args.srcPath : "No source path provided"
    assert args.dstPath: "No destinaion path provided"
    assert args.nodeLabel : "No node label provided"
    stashName = 'transfer'

    dir(args.srcPath){
        stash(name: stashName , includes: "./*" ) 
    }

    def nodes = [:]
    def names = getNodeNames(nodeLabel)
    for (int i=0; i<names.size(); ++i) {
        def nodeName = names[i];
        nodes[nodeName] = {
            node(nodeName) {
                dir(args.dstPath) {
                    unstash(name: stashName)
                }
            }
        }
    }
    parallel nodes
}

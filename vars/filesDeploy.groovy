@NonCPS
def getNodeNames(List<String> labels) {
    jenkins.model.Jenkins.instance.nodes
            .findAll { node -> node.getAssignedLabels()*.toString().intersect(labels) }
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

    parallel getNodeNames(args.nodeLabels).collectEntries { name ->
        [(name):
                 {node(name) {
                     dir(args.dstPath) {
                         unstash(name: stashName)
                     }
                 }}
        ]
    }
}

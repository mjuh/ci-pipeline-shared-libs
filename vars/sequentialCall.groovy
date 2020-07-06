def call(Map args = [:]) {
    assert args.procedure: "No procedure provided"
    assert args.nodeLabels : "No node labels provided"
    assert args.nodeLabels instanceof List<String> : "Node labels should be a list of strings"
    getNodeNames(args.nodeLabels).each { nodeName ->
        node(nodeName) { args.procedure() }
    }
}

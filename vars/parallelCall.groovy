def call(Map args = [:]) {
    assert args.procedure: "No procedure provided"
    assert args.nodeLabels : "No node labels provided"
    assert args.nodeLabels instanceof List<String> : "Node labels should be a list of strings"
    parallel (
        getNodeNames(args.nodeLabels).collectEntries { nodeName ->
            [(nodeName): { node(nodeName) { args.procedure() } }]
        }
    )
}

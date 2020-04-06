@NonCPS
def call(List<String> labels) {
    jenkins.model.Jenkins.instance.nodes
            .findAll { node -> node.getAssignedLabels()*.toString().intersect(labels) }
            .collect { node -> node.name }
}

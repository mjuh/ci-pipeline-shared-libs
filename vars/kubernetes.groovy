@Grab('org.apache.commons:commons-io:1.3.2')
import org.apache.commons.io.FilenameUtils

@NonCPS
def kustomizationFilesInChangeSets() {
    def output = []
    currentBuild.changeSets.each { changeSet ->
        changeSet.items.each { entry ->
            (new ArrayList(entry.affectedFiles)).each { file ->
                if (file.path.endsWith("kustomization.yaml") || file.path.endsWith("kustomization.yml")) {
                    output = output + file.path
                }
            }
        }
    }
    output
}

def lintKustomizations() {
    kustomizationFilesInChangeSets().each { file ->
        dir(FilenameUtils.getPath(file)) {
            warnError("malformated Kubernetes object") {
                sh "kubectl kustomize | nix shell nixpkgs#kube-linter --command kube-linter lint -"
            }
        }
    }
}

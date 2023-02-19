import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.json.JsonOutput

def kustomize(command) {
    sh "nix develop git+https://gitlab.intr/nixos/kubernetes --command kustomize ${command.join(" ")}"
}

def call(Map args = [:]) {
    pipeline {
        agent { label "jenkins" }
        options {
            timeout(time: 6, unit: "HOURS")
        }
        stages {
            stage("build") {
                steps {
                    sh "true"
                }
            }
        }
    }
}

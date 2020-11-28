import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def mergeMaps(first, second) {
    second.each {k, _ -> first[k] = first[k] instanceof Map ? mergeMaps(first[k], second[k]) : second[k] ?: first[k] }
    first
}

def call(Map args) {
    assert args.stack : "No stack name provided"

    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    def registry = args.registry ?: Constants.dockerRegistryHost
    def prodService = jsonParse(
        sh(returnStdout: true,
           script: "docker service inspect ${args.stack}_${args.service} 2>/dev/null || true").trim()
    )
    def imageName = null
    if(args.image) {
        imageName = args.image.imageName()
        imageName = "${registry}/" + (imageName - "${registry}/")
    }
    def dockerStacksRepoCommitId = args.dockerStacksRepoCommitId ?: 'master'

    dir("${env.HOME}/${Constants.dockerStacksDeployDir}") {
        lock(Constants.dockerStacksGitRepoUrl) {
            checkout([$class: 'GitSCM',
                      branches: [[name: dockerStacksRepoCommitId]],
                      userRemoteConfigs: [[url: Constants.dockerStacksGitRepoUrl, credentialsId: Constants.gitCredId]]])

            def stackConfigFile = args.stackConfigFile ?: "${args.stack}.yml"
            def stackDeclaration = readYaml(file: stackConfigFile)
            def imageUpdated = false

            if(args.service && imageName) {
                stackDeclaration.services."${args.service}".image = imageName
                sh "rm -f ${stackConfigFile}"
                if(stackDeclaration."x-${args.stack}-override" && stackDeclaration."x-${args.stack}-override".services) {
                    mergeMaps(stackDeclaration.services, stackDeclaration."x-${args.stack}-override".services)
                    mergeMaps(stackDeclaration.networks, stackDeclaration."x-${args.stack}-override".networks)
                }
                writeYaml(file: stackConfigFile, data: stackDeclaration)
                imageUpdated = true
            }

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                              usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
                sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
                if (args.service && prodService && imageName && !stackDeclaration."x-${args.stack}-override") {
                    def serviceDeclaration = stackDeclaration.services."${args.service}"
                    if(!serviceDeclaration) { error "${args.service} is not declared in ${args.stack}.yml" }
                    echo """
                        Service ${args.service} exists,
                        name: ${prodService.Spec.Name}
                        image: ${prodService.Spec.TaskTemplate.ContainerSpec.Image}
                        mode: ${prodService.Spec.Mode}
                    """.stripMargin().stripIndent()
                    def cmd = 'docker service update --detach=false --with-registry-auth --force '
                    if(serviceDeclaration.deploy.replicas) {
                        cmd += "--replicas ${serviceDeclaration.deploy.replicas} "
                    }
                    if(serviceDeclaration.deploy.placement.constraints) {
                        cmd += serviceDeclaration.deploy.placement.constraints.collect {"--constraint-add \"${it}\" "}.join()
                    }
                    if(serviceDeclaration.ports) {
                        cmd += serviceDeclaration.ports.collect {"--publish-add ${it} "}.join()
                    }
                    if(serviceDeclaration.environment) {
                        cmd += serviceDeclaration.environment.collect {"--env-add ${it} "}.join()
                    }
                    if(serviceDeclaration.command) {
                        cmd += serviceDeclaration.command.collect {"--args ${it} "}.join()
                    }
                    cmd += "--image ${imageName} ${args.stack}_${args.service}"
                    sh cmd
                } 
                else if(stackDeclaration."x-${args.stack}-override" && stackDeclaration."x-${args.stack}-override".services) {
                    sh "docker stack deploy --with-registry-auth -c ${stackConfigFile} ${args.stack}"
                }
                else {
                    sh "docker stack deploy --with-registry-auth -c ${stackConfigFile} ${args.stack}"
                }
            }
            if(imageUpdated) {
                createSshDirWithGitKey(dir: HOME + '/.ssh')
                sh """
                    git config --global user.name 'jenkins'
                    git config --global user.email 'jenkins@majordomo.ru'
                    git stash
                   """
                if (((sh (script: "git stash list", returnStdout: true)).trim()) != "") {
                    sh """
                          git checkout master
                          git pull origin master
                          git stash pop
                          git add ${stackConfigFile}
                          git commit -m '${args.stack}/${args.service} image updated: ${imageName}'
                          git push origin master
                       """
                    String.format("Jenkins pushed %s to $Constants.dockerStacksGitRepoUrl master",
                                  (sh (script: "git rev-parse HEAD", returnStdout: true)).trim())
                } else {
                    "Jenkins skipped push to $Constants.dockerStacksGitRepoUrl master"
                }
            }
        }
    }
}


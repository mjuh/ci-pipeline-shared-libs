def call(Map args) {
    assert args.project : "No project name provided"
    assert args.service : "No service name provided"

    def credentialsId = args.credentialsId ?: Constants.dockerRegistryCredId
    def registry = args.registry ?: Constants.dockerRegistryHost

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

            def projectConfigFile = "${args.project}.yml"
            def stackDeclaration = readYaml(file: projectConfigFile)
            def containerNameBase = "${project}_service_"
            def serviceExists = sh(returnStdout: true,
                                   script: "docker ps --format '{{.Names}}' -a").contains(containerNameBase)
            def serviceRunning = sh(returnStdout: true,
                                    script: "docker ps --format '{{.Names}}' --filter status=running").contains(containerNameBase)
            def imageUpdated = false

            if(args.service && imageName && stackDeclaration.services."${args.service}".image != imageName) {
                stackDeclaration.services."${args.service}".image = imageName
                sh "rm -f ${projectConfigFile}"
                writeYaml(file: projectConfigFile, data: stackDeclaration)
                imageUpdated = true
            }

            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                              usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
                sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
                def serviceDeclaration = stackDeclaration.services."${args.service}"
                if(!serviceDeclaration) { error "${args.service} is not declared in ${args.stack}.yml" }
                if(serviceRunning){ sh "docker-compose -f ${projectConfigFile} stop ${args.service}" }
                if(serviceExists){ sh "docker-compose -f ${projectConfigFile} rm -f ${args.service}" }
                sh "docker-compose -f ${projectConfigFile} create ${args.service}"
                sh "docker-compose -f ${projectConfigFile} start ${args.service}"
                }
            if(imageUpdated) {
                createSshDirWithGitKey(dir: HOME + '/.ssh')
                sh """
                    git config --global user.name 'jenkins'
                    git config --global user.email 'jenkins@majordomo.ru'
                    git stash
                    git checkout master
                    git pull origin master
                    git stash pop
                    git add ${stackConfigFile}
                    git commit -m '${args.stack}/${args.service} image updated: ${imageName}'
                    git push origin master
                """
            }
        }
    }
}

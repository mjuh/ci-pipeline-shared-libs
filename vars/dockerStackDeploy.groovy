import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def call(Map args) {
	assert args.stack : "No stack name provided"

	def credentialsId = args.credentialsId ?: dockerRegistryCredId
	def prodService = jsonParse(
		sh(returnStdout: true,
           script: "docker service inspect ${args.stack}_${args.service} 2>/dev/null || true").trim()
	)
	def service = prodService ? args.service : ''
	def ns = args.namespace ?: args.stack
	def image = args.image ?: args.service
	def tag = args.tag ?: dockerImageDefaultTag 
	def registry = args.registry ?: dockerRegistryHost

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                    usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
		sh "docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD ${registry}"
		if(service) {
			sh "docker service update --with-registry-auth --force --image ${registry}/${ns}/${image}:${tag} ${args.stack}_${service}"
		} else {
			dir(env.HOME + '/docker-stacks') {
				git(url: dockerStacksGitRepoUrl,
					credentialsId: gitCredId)
			}
			sh "docker stack deploy --with-registry-auth -c $HOME/docker-stacks/${args.stack}.yml ${args.stack}"
		}
	}
}

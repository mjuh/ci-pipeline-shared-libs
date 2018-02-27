import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def call(Map args) {
	assert args.stack : "No stack name provided"

	def credentialsId = args.credentialsId ?: "docker-registry"
	def prodService = jsonParse(
		sh(returnStdout: true,
           script: "docker service inspect ${args.stack}_${args.service} 2>/dev/null || true").trim()
	)

	env.STACK = args.stack
	env.SERVICE = prodService ? args.service : ''
	env.NS = args.namespace ?: args.stack
	env.IMAGE = args.image ?: args.service
	env.TAG = args.tag ?: "master"
	env.REGISTRY = args.registry ?: "docker-registry.intr"

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                    usernameVariable: 'REGISTRY_USERNAME', passwordVariable: 'REGISTRY_PASSWORD']]) {
		sh 'docker login -u $REGISTRY_USERNAME -p $REGISTRY_PASSWORD $REGISTRY'
		if(env.SERVICE) {
			sh 'docker service update --with-registry-auth --force --image $REGISTRY/$NS/$IMAGE:$TAG $STACK_$SERVICE'
		} else {
			dir(env.HOME+'/docker-stacks') {
				git(url: 'git@gitlab.intr:_ci/docker-stacks.git',
					credentialsId: 'd8f04931-9047-413a-80f3-eef23003522c')
			}
			sh 'docker stack deploy --with-registry-auth -c $HOME/docker-stacks/$STACK.yml $STACK'
		}
	}
}

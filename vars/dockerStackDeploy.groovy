import groovy.json.JsonSlurper

def call(Map args) {
	assert args.stack : "No stack name provided"

	def credentialsId = args.credentialsId ?: "docker-registry"
	def jsonSlurper = new JsonSlurper()
	def dockerServiceInspect = sh(returnStdout: true, script: "docker service inspect ${args.stack}_${args.service} 2>/dev/null").trim()
	println dockerServiceInspect
	def prodService = jsonSlurper.parseText(dockerServiceInspect)

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
			git(url: 'git@gitlab.intr:_ci/docker-stacks.git')
			sh 'docker stack deploy --with-registry-auth -c ./docker-stacks/$STACK.yml $STACK'
		}
	}
}

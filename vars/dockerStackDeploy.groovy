def call(Map args) {

	assert args.stack : "No stack name provided"
	assert args.service : "No service name provided"

	def credentialsId = args.credentialsId ?: "docker-registry"

	env.NS = args.namespace ?: args.stack
	env.IMAGE = args.image ?: args.service
	env.TAG = args.tag ?: "master"
	env.REGISTRY = args.registry ?: "docker-registry.intr"

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
		sh '''
			docker login -u ${USERNAME} -p ${PASSWORD} ${REGISTRY}
			docker pull ${REGISTRY}/${NS}/${IMAGE}:${TAG}
		'''
	}
}

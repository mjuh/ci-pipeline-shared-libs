def call(Map args) {

	assert args.stack : "No stack name provided"
	assert args.service : "No service name provided"

	def namespace = args.namespace ?: args.stack
	def image = args.image ?: args.service
	def tag = args.tag ?: "master"
	def registry = args.registry ?: "docker-registry.intr"
	def credentialsId = args.credentialsId ?: "docker-registry"

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
		sh '''
			docker login -u ${USERNAME} -p ${PASSWORD} ${registry}
			docker pull ${registry}/${namespace}/${image}:${tag}
		'''
	}
}

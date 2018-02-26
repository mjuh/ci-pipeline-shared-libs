def call(Map args) {

	assert args.image : "No image name provided"
	assert args.stack : "No stack name provided"
	assert args.service : "No service name provided"

	def tag = agrs.tag ?: "master"
	def registry = args.registry ?: "docker-registry.intr"
	def credentialsId = args.credentialsId ?: "docker-registry"

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId,
                    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
		sh '''
			docker login -u ${USERNAME} -p ${PASSWORD} ${registry}
			docker pull ${registry}/${args.stack}/${args.service}:${tag}
		'''
	}
}

def call(Map args) {

	assert args.image : "No image name provided"
	
	def tag = args.tag ?: "latest"
	def dockerfile = args.dockerfile ?: "Dockerfile"
	def dockerfileDir = args.dockerfileDir ?: "."
	def registryUrl = args.registryUrl ?: "https://docker-registry.intr"
	def credentialsId = args.credentialsId ?: "docker-registry"

	docker.withRegistry(registryUrl, credentialsId) {
		def dockerImage = docker.build("${args.image}:${tag}", "-f ${dockerfile} ${dockerfileDir}")
		dockerImage.push()
	}
}

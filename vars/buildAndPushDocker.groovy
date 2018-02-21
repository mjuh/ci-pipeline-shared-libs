def call(Map args = [:]) {
	def tag = args.tag ?: ${env.BRANCH_NAME}
	def image = docker.build("my-image:${tag}")	
    docker.withRegistry('https://docker-registry.intr', 'docker-registry') {
        image.push()
    }

}

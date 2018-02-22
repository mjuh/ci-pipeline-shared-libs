def call(Map args) {
	docker.withRegistry('https://docker-registry.intr', 'docker-registry') {
		def dockerImage = docker.build("${args.image}:${args.tag}")
		dockerImage.push()
	}
}

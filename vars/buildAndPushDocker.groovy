def call(Map args) {
	node {
		docker.withRegistry('https://docker-registry.intr', 'docker-registry') {
			def image = docker.build("test:test")
			image.push()
		}
	}
}

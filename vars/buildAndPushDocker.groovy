def call(Map args) {
    docker.withRegistry('https://docker-registry.intr', 'docker-registry') {
		def image = docker.build("${image}:${tag}")	
        image.push()
    }
}

def call(Map args) {
    docker.withRegistry('https://docker-registry.intr', 'docker-registry') {
		def image = docker.build("${args.image}:${args.tag}")	
        image.push()
    }
}

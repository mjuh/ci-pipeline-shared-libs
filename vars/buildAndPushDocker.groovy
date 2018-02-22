def call(Map args) {
	def image = docker.build("${args.image}:${args.tag}")	
	image.push()
}

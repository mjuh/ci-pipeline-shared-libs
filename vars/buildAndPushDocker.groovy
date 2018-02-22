def call(Map args) {
	node {
//		docker.withRegistry('https://docker-registry.intr', 'docker-registry') {
//			def dockerImage = docker.build("${args.image}:${args.tag}")
//			dockerImage.push()
//		}
	sh 'printenv'
	}
}

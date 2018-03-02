def call(Map args) {
	def cmd = args.cmd ?: 'install'
	def registry = args.registry ?: Constants.dockerRegistryHost
	def dockerCredId = args.credentialsId ?: Constants.dockerRegistryCredId
	def composerNs = args.composerDockerNamespace ?: Constants.composerDockerNamespace 
	def composerImage = args.composerDockerImage ?: Constants.composerDockerImage
	def composerTag = args.composerDockerTag ?: Constants.composerDockerTag
	def composer = "${registry}/${composerNs}/${composerImage}:${composerTag}"
	def phpVersion = args.phpVersion ?: 'php56'
	def srcDir = args.srcDir ?: 'src'
	def jenkinsHomeOnHost = new JenkinsContainer().getMountByDestination(env.HOME).Source
	def uid = sh(returnStdout: true, script: 'id -u').trim()
	def workspaceOnHost = jenkinsHomeOnHost + (env.WORKSPACE - env.HOME)

	writeFile(file: 'composer-passwd', text: "jenkins:x:${uid}:${uid}:,,,,:/home/jenkins:/bin/bash")
	sh "mkdir -p $HOME/composer-tmp"
	sh "cp -R $WORKSPACE/${srcDir}/ $WORKSPACE/build"

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: dockerCredId,
                      usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
		sh "docker login -u $USERNAME -p $PASSWORD ${registry}"
		sh """docker run --rm										\
			--user ${uid}:${uid}									\
			-e 'PHP_VERSION=${phpVersion}'							\
			-v ${workspaceOnHost}/composer-passwd:/etc/passwd:ro	\
			-v $HOME/composer-tmp:/composer							\
			-v $HOME:/home/jenkins									\
			-v ${workspaceOnHost}/build:/app						\
			${composer} ${cmd}"""
	}
}

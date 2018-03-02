def call(String cmd, Map args) {
	def registry = args.registry ?: Constants.dockerRegistryHost
	def dockerCredId = args.credentialsId ?: Constants.dockerRegistryCredId
	def composerNs = args.composerDockerNamespace ?: Constants.composerDockerNamespace 
	def composerImage = args.composerDockerImage ?: Constants.composerDockerImage
	def composerTag = args.composerDockerTag ?: Constants.composerDockerTag
	def composer = "${registry}/${composerNs}/${composerImage}:${composerTag}"
	def phpVersion = args.phpVersion ?: 'php56'
	def srcDir = args.srcDir ?: 'src'

	def jenkinsHomeOnHost = jenkinsContainer.getMountByDestination(env.HOME).Source
	def uid = sh(returnStdout: true, script: 'id -u').trim()
	def workspaceOnHost = jenkinsHomeOnHost + env.WORKSPACE - env.HOME
	def passwd = new File('./composer-passwd')

	passwd << 'jenkins:x:${uid}:${uid}:,,,,:/home/jenkins:/bin/bash'
	sh "mkdir -p $HOME/composer-tmp"
	sh "cp -R ${srcDir} build"

	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: dockerCredId,
                    usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
		sh "docker login -u $USERNAME -p $PASSWORD ${registry}"
		sh "docker run --rm --user ${uid}:${uid} -e 'PHP_VERSION=${phpVersion}' -v ./composer-passwd:/etc/passwd:ro -v $HOME/composer-tmp:/composer -v $HOME:/home/jenkins -v ${workspaceOnHost}/build:/app ${composer} ${cmd}"
	}
}
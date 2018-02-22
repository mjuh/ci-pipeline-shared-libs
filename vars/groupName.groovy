def call() {
	def git = GrgitWrapper.open(file('.'))
	def url = git.remote.list().find { it.name == 'origin' }
	println url
}

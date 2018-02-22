def call() {
	def git = Grabbed.Grgit.open(file('.'))
	def url = git.remote.list().find { it.name == 'origin' }
	println url
}

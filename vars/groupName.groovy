@Grab('org.ajoberstar:grgit:2.0.1')

def call() {
	def git = org.ajoberstar.grgit.Grgit.open(file('.'))
	def url = git.remote.list().find { it.name == 'origin' }
	println url
}

@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def call() {
	def git = Grgit.open(dir: env.WORKSPACE)
	def url = git.remote.list().find { it.name == 'origin' }
	println url
}

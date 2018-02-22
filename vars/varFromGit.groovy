@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def getProject() {
	def url = Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }.url
	url.split(':').tail().join(':').split('/|\\.')[-2]
}

def getGroup() {
	def url = Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }.url
	url.split(':').tail().join(':').split('/|\\.')[-3]
}

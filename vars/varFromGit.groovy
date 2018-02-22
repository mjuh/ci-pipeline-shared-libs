@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def getUrl() {
	Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }.url
}

def getProject() {
	getUrl().split(':').tail().join(':').split('/|\\.')[-2]
}

def getGroup() {
	getUrl().split(':').tail().join(':').split('/|\\.')[-3]
}

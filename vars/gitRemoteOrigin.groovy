@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def getRemote() {
    Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }
}

def getProject() {
    getRemote().url.split(':').tail().join(':').split('/|\\.')[-2]
}

def getGroup() {
    getRemote().url.split(':').tail().join(':').split('/|\\.')[-3]
}

@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def getRemote() {
    Grgit.open(dir: '.').remote.list().find { it.name == 'origin' }
}

def getProject() {
    (getRemote().url.split(':').tail().join(':').split('/').tail().join('/') - '.git').toLowerCase()
}

def getGroup() {
    getRemote().url.split(':').tail().join(':').split('/')[0].toLowerCase()
}

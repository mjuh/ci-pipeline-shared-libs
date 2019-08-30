@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def getRemote() {
    Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }
}

def getProject() {
    def url = env.GIT_URL ?: getRemote().url
    (url.split(':').tail().join(':').split('/').tail().join('/') - '.git').toLowerCase()
}

def getGroup() {
    def url = env.GIT_URL ?: getRemote().url
    url.split(':').tail().join(':').split('/')[0].toLowerCase()
}

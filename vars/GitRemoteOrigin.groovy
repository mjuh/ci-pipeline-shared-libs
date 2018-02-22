@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

class GitRemoteOrigin {
    static void main(_) {}
    static Remote getUrl() {
        Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }
    }
    static final String getGroup() {
        this.getRemote().url.split(':').tail().join(':').split('/|\\.')[-3]
    }
    static final String getProject() {
        this.getRemote().url.split(':').tail().join(':').split('/|\\.')[-2]
    }
}


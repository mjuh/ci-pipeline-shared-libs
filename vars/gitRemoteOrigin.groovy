@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

class GitRemoteOrigin {
    static void main(_) {}
    static Remote getUrl() {
        Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }
    }
    static final String group = this.getRemote().url.split(':').tail().join(':').split('/|\\.')[-3]
    static final String project = this.getRemote().url.split(':').tail().join(':').split('/|\\.')[-2]
}


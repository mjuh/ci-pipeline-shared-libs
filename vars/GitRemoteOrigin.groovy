@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Remote 

class GitRemoteOrigin {
    static void main(_) {}
    static Remote getRemote() {
        Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == 'origin' }
    }
    static final String getGroup() {
        this.getRemote().url.split(':').tail().join(':').split('/|\\.')[-3]
    }
    static final String getProject() {
        this.getRemote().url.split(':').tail().join(':').split('/|\\.')[-2]
    }
}


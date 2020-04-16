@Grab('org.ajoberstar:grgit:2.0.1')
import org.ajoberstar.grgit.Grgit

def getRemote(remote) {
    Grgit.open(dir: env.WORKSPACE).remote.list().find { it.name == remote }
}

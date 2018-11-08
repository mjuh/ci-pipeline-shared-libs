import groovy.json.JsonSlurperClassic

class JenkinsContainer {
    private Map info = new groovy.json.JsonSlurperClassic().parseText(
        "docker inspect $HOSTNAME".execute().text.trim()
    )[0]

    public getMountByDestination(String destination) {
        this.info.Mounts.find { it.Destination == destination }
    }

    public getHostPath(String path) {
        def hostPath = null
        def mount = this.info.Mounts.find { path.startsWith(it.Destination) }
        if(mount) {
            hostPath = mount.Source + path - mount.Destination
        }
        hostPath
    }

    public getUid() {
        "id -u".execute().text.trim()
    }
}


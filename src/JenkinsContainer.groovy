import groovy.json.JsonSlurperClassic

class JenkinsContainer {
	private Map info = new groovy.json.JsonSlurperClassic().parseText(
		sh(returnStdout: true,
            script: "docker inspect ${Constants.jenkinsContainerName}").trim()
		)
    public getInfo() {
        this.info
    }
	public getMountByDestination(String destination) {
		this.getInfo().[0].Mounts.find { it.Destination == destination }
	}
}

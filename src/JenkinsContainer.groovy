import groovy.json.JsonSlurperClassic

class JenkinsContainer {
	private Map info = new groovy.json.JsonSlurperClassic().parseText(
		"docker inspect ${Constants.jenkinsContainerName}".execute().text.trim()
	)
	public getInfo() {
		this.info
	}
	public getMountByDestination(String destination) {
		this.info[0].Mounts.find { it.Destination == destination }
	}
}


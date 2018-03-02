import groovy.json.JsonSlurperClassic

class JenkinsContainer {
	private Map info = new groovy.json.JsonSlurperClassic().parseText(
		"docker inspect ${Constants.jenkinsContainerName}".execute().text.trim()
	)[0]

	public getMountByDestination(String destination) {
		this.info.Mounts.find { it.Destination == destination }
	}
}


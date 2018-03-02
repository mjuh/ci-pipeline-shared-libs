import groovy.json.JsonSlurperClassic

class JenkinsContainer {
	public getInfo() {
		new groovy.json.JsonSlurperClassic().parseText(
			"docker inspect ${Constants.jenkinsContainerName}".execute().text.trim()
		)
	}
	public getMountByDestination(String destination) {
		this.getInfo()[0].Mounts.find { it.Destination == destination }
	}
}


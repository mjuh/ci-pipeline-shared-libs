import groovy.json.JsonSlurperClassic

class JenkinsContainer {
	public getInfo() {
		new groovy.json.JsonSlurperClassic().parseText(jsonParse(
			sh(returnStdout: true,
            script: "docker inspect ${Constants.jenkinsContainerName}").trim()
		))
	}
	public getMountByDestination(String destination) {
		this.getInfo()[0].Mounts.find { it.Destination == destination }
	}
}


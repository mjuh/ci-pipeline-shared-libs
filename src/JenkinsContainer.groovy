import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

class JenkinsContainer {
	public getInfo() {
		jsonParse(
			sh(returnStdout: true,
            script: "docker inspect ${Constants.jenkinsContainerName}").trim()
		)
	}
	public getMountByDestination(String destination) {
		this.getInfo()[0].Mounts.find { it.Destination == destination }
	}
}

import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

class JenkinsContainer {
	private Map info = jsonParse(
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

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
	@NonCPS
    public getInfo() {
        this.info
    }
	@NonCPS
	public getMountByDestination(String destination) {
		this.getInfo().[0].Mounts.find { it.Destination == destination }
	}
}

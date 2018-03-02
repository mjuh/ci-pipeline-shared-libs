import groovy.json.JsonSlurperClassic

class JenkinsContainer {
	@NonCPS
	private Map info = new groovy.json.JsonSlurperClassic().parseText(
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

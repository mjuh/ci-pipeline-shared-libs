import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def getMountByDestination(String destination) {
	jsonParse(
		sh(returnStdout: true,
		   script: "docker inspect ${Constants.jenkinsContainerName}").trim()
	).[0].Mounts.find { it.Destination == destination }
}

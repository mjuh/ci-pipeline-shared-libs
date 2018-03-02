import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

@NonCPS
def getInfo() {
	jsonParse(sh(returnStdout: true,
                 script: "docker inspect ${Constants.jenkinsContainerName}").trim())
}

def getMountByDestination(String destination) {
	getInfo()[0].Mounts.find { it.Destination == destination }
}

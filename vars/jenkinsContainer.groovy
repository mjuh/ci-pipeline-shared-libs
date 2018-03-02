import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def getInfo() {
	jsonParse(sh(returnStdout: true,
                 script: "docker inspect ${Constants.jenkinsContainerName}").trim())
}

@NonCPS
def getMountByDestination(String destination) {
	println getInfo()
	getInfo()[0].Mounts.find { it.Destination == destination }
}

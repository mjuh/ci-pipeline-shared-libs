def getInfo() {
	Utils.jsonParse(sh(returnStdout: true,
                       script: "docker inspect ${Constants.jenkinsContainerName}").trim())
}

def getMountByDestination(String destination) {
	getInfo()[0].HostConfig.Mounts.find { it.Destination == destination }
}

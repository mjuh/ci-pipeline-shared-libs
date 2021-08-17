def getProject(jobName) {
    jobName.split("%2F")[1].split("/")[0]
}

def getGroup(jobName) {
    jobName.split("%2F")[0].split("/")[0]
}

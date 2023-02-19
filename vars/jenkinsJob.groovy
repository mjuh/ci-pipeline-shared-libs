def getProject(jobName) {
    jobName.split("/")[1]
}

def getGroup(jobName) {
    jobName.split("/")[0]
}

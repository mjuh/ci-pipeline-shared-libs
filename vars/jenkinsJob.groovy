def getProject() {
    env.JOB_NAME.split("/")[1]
}

def getGroup() {
    env.JOB_NAME.split("/")[0]
}

def getProject() {
    env.JOB_NAME.split("%2F")[1].split("/")[0]
}

def getGroup() {
    env.JOB_NAME.split("%2F")[0].split("/")[0]
}

def getProject() {
    env.JOB_NAME.split("%2F")[1]
}

def getGroup() {
    env.JOB_NAME.split("%2F")[0]
}

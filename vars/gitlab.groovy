def running(String name = STAGE_NAME) {
    updateGitlabCommitStatus(name: name, state: 'running')
}

def success(String name = STAGE_NAME) {
    updateGitlabCommitStatus(name: name, state: 'success')
}

def failed(String name = STAGE_NAME) {
    updateGitlabCommitStatus(name: name, state: 'failed')
}

def failure(String name = STAGE_NAME) {
    failed(name)
}

def canceled(String name = STAGE_NAME) {
    updateGitlabCommitStatus(name: name, state: 'canceled')
}

def aborted(String name = STAGE_NAME) {
    canceled(name)
}

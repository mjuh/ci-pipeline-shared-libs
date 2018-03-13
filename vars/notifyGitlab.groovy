def call(String state, String name = STAGE_NAME) {
    def gitlabState = [running: 'running',
                       success: 'success',
                       failed: 'failed',
                       failure: 'failed',
                       canceled: 'canceled',
                       aborted: 'canceled'][state]
    updateGitlabCommitStatus(name: name, state: gitlabState)
}

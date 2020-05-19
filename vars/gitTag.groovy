// Return current tag if HEAD's commit is equal to tag's commit.
def call() {
    if ((sh (script: "git tag --list", returnStdout: true)) != "") {
        String HEAD = sh (
            script: "git rev-parse HEAD",
            returnStdout: true
        )
        String tagName = (sh (script: "git describe --tags --abbrev=0",
                              returnStdout: true)).trim()
        String tagCommit = sh (
            script: "git rev-list --max-count=1 $tagName",
            returnStdout: true
        )
        if (HEAD == tagCommit) {
            tagName
        } else {
            ""
        }
    }
}

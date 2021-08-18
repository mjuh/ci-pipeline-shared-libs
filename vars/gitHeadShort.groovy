def call() {
    (sh (script: "git rev-parse HEAD", returnStdout: true)).trim().take(8)
}

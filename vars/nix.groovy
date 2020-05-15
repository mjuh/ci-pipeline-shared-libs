def version() {
    sh (
        script: "nix-instantiate --eval --expr '(import <nixpkgs> {}).lib.version'",
        returnStdout: true
    ).trim()
}

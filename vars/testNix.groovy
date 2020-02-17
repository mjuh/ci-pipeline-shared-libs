def call(Map args = [:]) {
    List<String> nixArgs = args.nixArgs ?: [""]

    sh "nix-build test.nix ${nixArgs.join(' ')} --show-trace --out-link result-test"
    archiveArtifacts (artifacts: "result-test/**")
}

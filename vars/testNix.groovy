def call(Map args = [:]) {
    List<String> nixArgs = args.nixArgs ?: [""]

    sh "nix-build --out-link result/${env.JOB_NAME}/test-${env.BUILD_NUMBER} test.nix ${nixArgs.join(' ')} --show-trace --out-link result-test"
    archiveArtifacts (artifacts: "result-test/**")
}

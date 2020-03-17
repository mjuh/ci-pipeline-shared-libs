def call(Map args = [:]) {
    assert args.nixFile : "No image name provided"

    List<String> nixArgs = args.nixArgs ?: [""]

    sh ([ "nix-build",
          "--out-link",
          "result/${env.JOB_NAME}/$args.nixFile-${env.BUILD_NUMBER}", "${args.nixFile}.nix",
         nixArgs.join(' '), "--show-trace"].join(" "))

    archiveArtifacts (artifacts: "result/${env.JOB_NAME}/$args.nixFile-${env.BUILD_NUMBER}/**")
}

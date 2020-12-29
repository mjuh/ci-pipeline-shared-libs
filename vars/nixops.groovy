def deploy(Map args = [:]) {
    ["nixops", "deploy", "--show-trace",
     "--deployment", args.deployment,
     "--max-concurrent-activate", args.maxConcurrentActivate,
     args.args.join(" ")].join(" ")
}

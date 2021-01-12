def deploy(Map args = [:]) {
    (["nixops", "deploy", "--deployment", args.deployment]
     + (args.showTrace instanceof Boolean ? ["--show-trace"] : [])
     + (args.maxConcurrentActivate instanceof Number ? ["--max-concurrent-activate", args.maxConcurrentActivate] : [])
     + args.args).join(" ")
}

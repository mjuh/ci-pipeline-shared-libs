def call(Map args = [:]) {
    if (!args.imageName) {
        assert args.currentProjectBranch : "No current project branch provided"
    }

    String overlaybranch = args.overlaybranch ?: "master"
    String tag = gitTag()

    if (tag) tag
    else if (overlaybranch == args.currentProjectBranch) overlaybranch
    else (overlaybranch + "_" + args.currentProjectBranch)
}

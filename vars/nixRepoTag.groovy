def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.currentProjectBranch : "No current project branch provided"
    }

    String overlaybranch = args.overlaybranch ?: "master"

    if (overlaybranch == args.currentProjectBranch) {
        return overlaybranch
    } else {
        return (overlaybranch + "_" + args.currentProjectBranch)
    }
}

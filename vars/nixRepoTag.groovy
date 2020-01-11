def call(Map args = [:]) {
    if(!args.imageName) {
        assert args.overlaybranch : "No Nix overlay branch provided"
        assert args.currentProjectBranch : "No current project branch provided"
    }

    def overlaybranch = args.overlaybranch ?: "master"

    if (overlaybranch == args.currentProjectBranch) {
        repoTag = overlaybranch
    } else {
        repoTag = overlaybranch + "_" + args.currentProjectBranch
    }

    repoTag
}

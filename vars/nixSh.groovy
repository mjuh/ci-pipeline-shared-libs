def call(Map args = [:]) {
    assert args.cmd: "No cmd provided"

    def env = ['sh', '-c', '. .nix-profile/etc/profile.d/nix.sh && env'].execute().text.trim().split('\n').collect {it}
    if (args.env) {
        env += args.env.collect { it }
    }
    withEnv(env) {
        def pkgs = ['nix']
        if (args.pkgs) {
            pkgs += args.pkgs
        }
        def pkgStr = pkgs.collect { "-p ${it}" }.join(' ')
        ['sh', '-c', "nix-shell --quiet ${pkgStr} --run '${args.cmd}'"].execute().text
    }
}

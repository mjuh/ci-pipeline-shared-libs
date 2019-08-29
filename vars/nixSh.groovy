def call(Map args = [:]) {
    assert args.cmd: "No cmd provided"

    def env = ['sh', '-c', '. .nix-profile/etc/profile.d/nix.sh && env'].execute().text.trim().split('\n')
    if (args.env) {
        env += args.env.collect { it }
    }
    withEnv(env as java.util.List<java.lang.String>) {
        def pkgs = ['nix']
        if (args.pkgs) {
            pkgs += args.pkgs
        }
        def pkgStr = pkgs.collect { "-p ${it}" }.join(' ')
        ['sh', '-c', "nix-shell --quiet ${pkgStr} --run '${args.cmd}'"].execute().text
    }
}

def call(Map args = [:]) {
    assert args.cmd : "No cmd provided"

    def home = new File(args.home ?: env.HOME)
    def env = ['sh', '-c', '. .nix-profile/etc/profile.d/nix.sh && env'].execute(null, home).text.trim().split('\n')
    def pkgs = ['nix']
    if (args.env) {
        env += args.env.collect { it }
    }
    if (args.pkgs) {
        pkgs += args.pkgs
    }
    def pkgStr = pkgs.collect { "-p ${it}" }.join(' ')
    ['sh', '-c', "nix-shell --quiet ${pkgStr} --run '${args.cmd}'"].execute(env, home).text
}

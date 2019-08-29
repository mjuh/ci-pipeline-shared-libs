import com.cloudbees.groovy.cps.NonCPS

class NixShell {
    private File home
    private List<String> env
    private List<String> pkgs

    @NonCPS
    NixShell(Map args = [:]) {
        this.home = new File(args.home ?: System.getenv('HOME'))
        this.env = ['sh', '-c', '. .nix-profile/etc/profile.d/nix.sh && env'].execute(null, this.home).text.trim().split('\n')
        this.pkgs = ['nix']
        if (args.env) {
            this.env += args.env.collect { it }
        }
        if (args.pkgs) {
            this.pkgs += args.pkgs
        }
    }

    public run(String cmd) {
        def pkgStr = this.pkgs.collect { "-p ${it}" }.join(' ')
        ['sh', '-c', "nix-shell --quiet ${pkgStr} --run '${cmd}'"].execute(this.env, this.home).text
    }

}

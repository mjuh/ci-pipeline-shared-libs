def call(Map args = [:]) {
    assert args.cmd : "No cmd provided"

    def shell = new NixShell(home: args.home, env: args.env, pkgs: args.pkgs)
    shell.run(args.cmd)
}

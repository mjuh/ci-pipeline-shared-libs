def call(Map args = [:]) {
    assert args.cmd: "No cmd provided"

    def env = sh(script: """#!/bin/sh
                            . ${env.HOME}/.nix-profile/etc/profile.d/nix.sh
                            env
                         """, returnStdout: true).trim().split('\n')
    if (args.env) {
        env += args.env.collect { it }
    }
    withEnv(env as List) {
        def pkgs = ['nix']
        if (args.pkgs) {
            pkgs += args.pkgs
        }
        def pkgStr = pkgs.collect { "-p ${it}" }.join(' ')
        sh(script: """#!/bin/sh
                      nix-shell --quiet ${pkgStr} --run '${args.cmd}'
                   """, returnStdout: true).trim()
    }
}
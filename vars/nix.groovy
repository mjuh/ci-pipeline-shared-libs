// Workaround for "error: cannot lock ref 'refs/heads/master'"
// https://github.com/NixOS/nix/issues/4252
def nixBuildWrap() {
    sh "mkdir -p bin";
    writeFile(file: "bin/nix-build",
              text: '''
#!/usr/bin/env bash

trap "rm -f build.log" EXIT

for attempt in $(seq 10)
do
    printf "Attempt %s...\n" "$attempt" 1>2
    out="$(/run/current-system/sw/bin/nix-build "$@")"
    ret="${PIPESTATUS[0]}"
    if [[ $ret -ne 0 ]] && [[ $out == *"error: cannot lock ref"* ]]
    then
        sleep 60
    else
        printf "$out"
        exit 0
    fi
done

exit 1
''')
    sh "chmod +x bin/nix-build"
}

def saveResultDirectory(Map args = [:]) {
    """
if [[ -d result ]]
then
    exit 0
else
    printf "'result' file is not a directory, moving to ${args.backup}.\n"
    mv -v result ${args.backup}
fi
"""
}

def version() {
    sh (
        script: "nix-instantiate --eval --expr '(import <nixpkgs> {}).lib.version'",
        returnStdout: true
    ).trim()
}

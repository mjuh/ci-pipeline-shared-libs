def call(Map args = [:]) {
    switch (args.java) {
        case '8':
            sh 'nix-shell -I nixpkgs=https://github.com/NixOS/nixpkgs/archive/48723f48.tar.gz -p openjdk8 --run "gradle build"' // nixos-20.03
            break;
        case '11':
            sh 'nix-shell -I nixpkgs=https://github.com/NixOS/nixpkgs/archive/48723f48.tar.gz -p openjdk11 --run "gradle build"' // nixos-20.03
            break;
        case '14':
            sh 'nix-shell -I nixpkgs=https://github.com/NixOS/nixpkgs/archive/4214f76b.tar.gz -p openjdk14 --run "gradle build"' // nixos-unstable
            break;
        default:
            sh 'gradle build'
            break;
    }
}

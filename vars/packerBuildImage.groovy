def call(Map args = [:]) {
        assert args.template : "No template provided"
        assert args.output : "No output provided"
        assert args.vars : "No packervars provided"

        def template = args.template
        def output = args.output
        def vars = args.vars

    sh '''
        . /home/jenkins/.nix-profile/etc/profile.d/nix.sh ;
        packer build -force -var-file=vars/${vars}.json templates/${template}.json
        ls -alah */${output}
    '''
}

def call(Map args = [:]) {
        assert args.template : "No template provided"
        assert args.output : "No output provided"
        assert args.vars : "No packervars provided"

        def template = args.template
        def output = args.output
        def vars = args.vars

    sh """
        echo "vars: ${vars} templ: ${template} output: ${output}"
        echo "args.vars: ${args.vars} args.templ: ${args.template} args.output: ${args.output}"
        . /home/jenkins/.nix-profile/etc/profile.d/nix.sh ;
        packer build -force -var-file=vars/${vars}.json templates/${template}.json
        ls -alah */${output}

    """
}

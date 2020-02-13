def call(Map args = [:]) {
        assert args.template : "No template provided"
        assert args.output : "No output provided"
        assert args.vars : "No packervars provided"

        def template = args.template
        def output = args.output
        def vars = args.vars
        def upload = args.upload ?: false

    if(!upload){
    sh """
        .  ${env.HOME}/.nix-profile/etc/profile.d/nix.sh || true ;
        packer build -force -var-file=vars/${vars}.json templates/${template}.json
        ls -alah */${output}
    """
    }
    if(upload){
        if(env.BRANCH_NAME == "master"){
            sh """
                rm -f */${output} || true
                .  ${env.HOME}/.nix-profile/etc/profile.d/nix.sh || true ;
                packer build -force -var-file=vars/${vars}.json templates/${template}.json
                ls -alah */${output}
                rsync -av */${output} rsync://archive.intr/images/jenkins-production/
                rm -f */${output}
            """
        } else {
            sh """
                rm -f */${output} || true
                .  ${env.HOME}/.nix-profile/etc/profile.d/nix.sh || true ;
                packer build -force -var-file=vars/${vars}.json templates/${template}.json
                ls -alah */${output}
                rsync -av */${output} rsync://archive.intr/images/jenkins-development/
                rm -f */${output}
            """
        }

   }
}

def call(Map args = [:]) {
    assert args.template : "No template provided"
    assert args.vars : "No packervars provided"
    assert args.imageSize : "No imageSize provided"

    def vars = args.vars
    def upload = args.upload ?: false
    def template = args.template
    def output = args.output ?: sh(returnStdout: true, script: "jq -r .vm_name vars/${vars}.json").trim()

    // TODO: Don't use “*”
    String imageSize = "[[ \$(stat --format=%s */$output) < ${args.imageSize} ]] && exit 1" ?: "true"

    if(!upload){
        sh """
        packer build -force -var-file=vars/${vars}.json templates/${template}.json
        ls -alah */${output}
    """
    }
    if(upload){
        if(env.BRANCH_NAME == "master"){
            sh (["echo ${output}",
                 "rm -f */${output}",
                 "packer build -force -var-file=vars/${vars}.json templates/${template}.json",
                 imageSize,
                 "ls -alah */${output}",
                 "rsync -av */${output} rsync://archive.intr/images/jenkins-production/",
                 "rm -f */${output}"].join("; "))
        } else {
            sh """
                echo ${output}
                rm -f */${output}
                packer build -force -var-file=vars/${vars}.json templates/${template}.json
                ls -alah */${output}
                rsync -av */${output} rsync://archive.intr/images/jenkins-development/
                rm -f */${output}
            """
        }

    }
}

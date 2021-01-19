def deployCommand(input, output) {
    "rsync --archive --verbose ${input} rsync://archive.intr/images/${output}/"
}

def packer(Map args = [:]) {
    assert args.template instanceof String
    assert args.vars instanceof String

    String buildCommand =
        "packer build -force -var-file=vars/iso.json -var-file=vars/${args.vars}.json $WORKSPACE/templates/${args.template}.json"
    String image = args.output ?:
        sh(returnStdout: true,
           script: "jq --raw-output .vm_name $WORKSPACE/vars/${args.vars}.json").trim()
    String checkImageSizeCommand = args.imageSize ? // TODO: Don't use “*”
    "[[ \$(stat --format=%s $WORKSPACE/*/$image) < ${args.imageSize} ]]; exit 1" :
        "true"

    def shellCommands = []
    shellCommands += "packer validate -var-file=vars/iso.json -var-file=$WORKSPACE/vars/${args.vars}.json $WORKSPACE/templates/${args.template}.json"
    if (env.BRANCH_NAME == "master") {
        [buildCommand,
         checkImageSizeCommand,
         deployCommand ('*/' + image, "jenkins-production"),
         "echo 'http://archive.intr/jenkins-production/$image'"]
            .each{shellCommands += it}
    } else {
        [buildCommand,
         deployCommand ("$WORKSPACE/*/$image", "jenkins-development"),
         "echo 'http://archive.intr/jenkins-development/$image'"]
            .each{shellCommands += it}
    }

    ansiColor("xterm") { sh (shellCommands.join("; ")) }
}

@NonCPS
def random(list) {
    list.sort { Math.random() }
}

def call(Map args = [:]) {
    assert args.distribution instanceof String
    assert args.id instanceof Number
    assert args.release instanceof String

    Map tarifsAdministration = [
        a1: 30720,
        a2: 40960,
        a3: 61440
    ]
    Map tarifsPersonal = [
        lite: 30720,
        optima: 40960,
        bussines: 61440,
        lightNew: 10240 // deprecated
    ]
    Map tarifs = args.administration ? tarifsAdministration : tarifsPersonal
    List<String> names = getNodeNames(args.nodeLabels)
    Number concurrent = (currentBuild.getBuildCauses('hudson.triggers.TimerTrigger$TimerTriggerCause') ? tarifs.keySet().size() : 1) + names.size()
    List<String> nodeParameter = random(names) // will become an empty array
    Map stepsForParallel = [:]

    def imagesVariations = [[args.distribution], tarifs.keySet()].combinations()
    imagesVariations.collate((imagesVariations.size() >= args.nodeLabels.size() ? imagesVariations.size() - args.nodeLabels.size() : 1) ?: concurrent)
        .collect{ jobs ->
            if (nodeParameter != null && nodeParameter != []) {
                firstNodeName = nodeParameter.head()
                nodeParameter = nodeParameter - firstNodeName
            }
            jobs.collect { job ->
                job + firstNodeName
            }
        }
        .collect { jobs ->
            jobs.collectEntries {
                String distribution = it[0]
                String tarif = it[1]
                String nodeName = it[2]
                String fullName = distribution + "-" + args.release + "-" + tarif + "@" + nodeName
                [(fullName): {
                    node(nodeName) {
                        checkout scm
                        packer (
                            template: distribution,
                            vars: distribution + args.release.split("\\.").head() + "-" + tarif,
                            image: args.id.toString() + "-" + tarifs.tarif,
                            imageSize: args.imageSize
                        )
                    }
                }]
            }
        }.each {
            stepsForParallel << it
        }

    parallel stepsForParallel
}

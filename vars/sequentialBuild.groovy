def call(Map args = [:]) {
    args.downstream.each{ job ->
        warnError("Failed to build $job") {
            // Scan Multibranch Pipeline Now
            // https://stackoverflow.com/questions/54046172/how-to-scan-repository-now-from-a-jenkinsfile
            build(job: ("../" + job), wait: false, propagate: false)
            sleep 2 // scanning takes time
            // Make sure job's "suppress-scm-triggering" parameter setted to "true".
            build(job: ("../" + job + "/" + (args.branch == null ? "master" : args.branch)),
                  parameters: args.parameters)
        }
    }
}

def call(Map args = [:]) {
    wait = args.wait ? true : args.wait
    args.downstream
        .collate(getNodeNames(args.nodeLabels).size())
        .each{ jobs ->
        parallel (jobs.collectEntries { job -> [(job): {
                        warnError("Failed to build $job") {
                            build(
                                job: "../${job}/master",
                                parameters: args.parameters,
                                wait: wait
                            )
                        }
                    }
                ]
            }
        )
    }
}

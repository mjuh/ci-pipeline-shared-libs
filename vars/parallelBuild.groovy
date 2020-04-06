def call(Map args = [:]) {
    args.downstream
        .collate(getNodeNames(args.nodeLabels).size())
        .each{ jobs ->
        parallel (jobs.collectEntries { job -> [(job): {
                        warnError("Failed to build $job") {
                            build(
                                job: "../${job}/master",
                                parameters: args.parameters
                            )
                        }
                    }
                ]
            }
        )
    }
}

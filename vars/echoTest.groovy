def call(Map parameters) {
	parameters.each{ k, v -> println "${k}:${v}" }
}

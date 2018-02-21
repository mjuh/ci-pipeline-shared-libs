def call() {
	def tags = sh (
		script: 'git tag -l --points-at HEAD',
		returnStdout: true
	).trim().split()
	def branch = sh (
		script: 'git rev-parse --abbrev-ref HEAD',
		returnStdout: true
	).trim()

	if (tags.length) {
		tags[0]
	} else {
		branch
	}
}

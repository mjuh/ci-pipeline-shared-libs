def call(def Map args = [:]) {
    def dockerImage = null
    def slackMessages = [];
    String GRADLE_OPTS = args.GRADLE_OPTS == null ? "" : args.GRADLE_OPTS.join(' ')

    pipeline {
        agent { label "jenkins" }
        environment {
            GITLAB_PROJECT_NAME = jenkinsJob.getProject(env.JOB_NAME)
            GITLAB_PROJECT_NAMESPACE = jenkinsJob.getGroup(env.JOB_NAME)
            INACTIVE_STACK = nginx.getInactive("/hms")
            GRADLE_OPTS = "${GRADLE_OPTS}"
            GRADLE_USER_HOME = "/var/lib/jenkins"
        }
        parameters {
            string(
                name: "dockerStacksRepoCommitId",
                defaultValue: "",
                description: "ID коммита в репозитории ${Constants.dockerStacksGitRepoUrl}, " +
                    "если оставить пустым, при деплое будет использован последний коммит в master"
            )
            booleanParam(
                name: "skipToDeploy",
                defaultValue: false,
                description: "пропустить сборку и тестирование"
            )
            booleanParam(
                name: "switchStacks",
                defaultValue: false,
                description: "Свичнуть стэки?"
            )
        }
        options {
            buildDiscarder(
                logRotator(
                    numToKeepStr: "10",
                    artifactNumToKeepStr: "10"
                )
            )
        }
        tools {
            gradle (args.gradle ?: "4")
            jdk (args.java ?: "8")
        }
        stages {
            stage("Build Gradle") {
                when {
                    allOf {
                        not { expression { params.skipToDeploy } }
                        not { expression { params.switchStacks } }
                    }
                    beforeAgent true
                }
                steps {
                    script { (args.preBuild ?: { return true })() }
                    sh "java -version; gradle -version; gradle build"
                }
            }
        }
        post {
            always {
                archiveArtifacts (
                    artifacts: 'build/reports/**',
                    allowEmptyArchive: true,
                    followSymlinks: true,
                    onlyIfSuccessful: false
                )
            }
        }
    }
}

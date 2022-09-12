pipeline {
    agent { label "jenkins" }
    options  {
        buildDiscarder(logRotator(numToKeepStr: "10",
                                  artifactNumToKeepStr: "10"))
        disableConcurrentBuilds()
    }
    environment {
        PROJECT_NAME = gitRemoteOrigin.getProject()
        GROUP_NAME = gitRemoteOrigin.getGroup()
    }
    stages {
        stage("check") {
            steps {
                build (job: "../../${Constants.bfgJobName}/master",
                       parameters: [
                        string(name: "GIT_REPOSITORY_TARGET_URL",
                               value: gitRemoteOrigin.getRemote().url),
                        string(name: "PROJECT_NAME",
                               value: PROJECT_NAME),
                        string(name: "GROUP_NAME",
                               value: GROUP_NAME),
                    ])
            }
        }
    }
}

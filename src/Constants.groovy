public class Constants {
    def main (_) {}
    public static final String dockerRegistryHost = 'docker-registry.intr'
    public static final String dockerRegistryCredId = 'docker-registry'
    public static final String dockerImageDefaultTag = 'master'

    public static final String dockerStacksGitRepoUrl = 'git@gitlab.intr:_ci/docker-stacks.git'
    public static final String dockerStacksDeployDir = 'docker-stacks'

    public static final String gitCredId = 'd8f04931-9047-413a-80f3-eef23003522c'
    public static final String gitHost = 'gitlab.intr'

    public static final String gitLabConnection = 'gitlab.intr'

    public static final String productionNodeLabel = 'dhost-production'

    public static final String composerDockerImage = "${dockerRegistryHost}/base/mj-composer"
    public static final String composerDockerTag = 'master'

    public static final String containerStructureTestImage = 'gcr.io/gcp-runtimes/container-structure-test'

    public static final String slackChannel = '#git'
    public static final String slackTeam = 'mjru'
    public static final String slackToken = 'kyUUMqP0xTrsz2TmLUH0V8hV'

    public static final String gradleDefaultCommand = 'build'


    public static final String nginx1ApiUrl = 'http://nginx1.intr:8080'
    public static final String nginx2ApiUrl = 'http://nginx2.intr:8080'
    public static final String nginxAuthUser = 'jenkins'
    public static final String nginxAuthPass = 'jenkins4nginx'

}

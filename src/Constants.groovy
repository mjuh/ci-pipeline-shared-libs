public class Constants {
    def main (_) {}
    public static final String dockerRegistryHost = 'docker-registry.intr'
    public static final String dockerRegistryCredId = 'docker-registry'
    public static final String dockerImageDefaultTag = 'master'

    public static final String dockerStacksGitRepoUrl = 'git@gitlab.intr:_ci/docker-stacks.git'
    public static final String dockerStacksDeployDir = 'docker-stacks'

    public static final String gitCredId = 'gitlab-git'
    public static final String gitHost = 'gitlab.intr'

    public static final String gitLabConnection = 'gitlab.intr'

    public static final String developmentNodeLabel = 'dhost-development'
    public static final String productionNodeLabel = 'dhost-production'

    public static final String composerDockerImage = "${dockerRegistryHost}/base/mj-composer"
    public static final String composerDockerTag = 'master'

    public static final String containerStructureTestImage = 'gcr.io/gcp-runtimes/container-structure-test'

    public static final String gradleDefaultCommand = 'build'

    public static final String nexusUrl = 'http://nexus.intr'
    public static final String nexusCredId = 'jenkinsnexus'
    public static final String nexusDefaultRawRepo  = "plain-files"

    public static final String dockerRegistryBrowserUrl = 'http://docker-registry-browser.intr'

    public static final String nginx1ApiUrl = 'http://nginx1.intr:8080'
    public static final String nginx2ApiUrl = 'http://nginx2.intr:8080'
    public static final String nginxAuthUser = 'jenkins'
    public static final String nginxAuthPass = '***REMOVED***'

    public static final String githubOrganization = 'mjuh'

    public static final String nixOverlay = 'git@gitlab.intr:_ci/nixpkgs.git'
    public static final ArrayList<String> nixDeployments = ["ns", "router", "swarm", "web", "nginx", "webmail", "jenkins", "xyz"]
    public static final ArrayList<String> developmentNixMachines = ["nginx3", "jenkins2", "web98", "mx1", "mx2"]
    public static final ArrayList<String> nixReleases = ["20.09", "unstable"]
}

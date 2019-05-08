@Grab('io.github.http-builder-ng:http-builder-ng-core:1.0.3')
import java.io.File
import groovyx.net.http.HttpBuilder

@NonCPS
def upload(File file, String repoPath, String name, String user, String password) {
    println(user + ":" + password)
    HttpBuilder.configure {
        request.uri = Constants.nexusUrl
        request.auth.basic user, password
    }.put {
        request.uri.path = "/repository/${repoPath}/${name}"
        request.contentType = 'application/octet-stream'
        request.body = file
    }
    def url = "${Constants.nexusUrl}/repository/${repoPath}/${name}"
    println("${file} uploaded to ${url}")
    url
}
def call(Map args = [:]) {
    assert args.file : 'No file provided'
    String filePath = args.file
    def repo = args.repo ?: Constants.nexusDefaultRawRepo
    def group = args.group ? "/${args.group}" : ''
    def version = args.version ?: 'latest'
    def (name, ext) = filePath.split("/")[-1].split("\\.", 2) as List
    def versionedName = name + "-${version}." + ext
    if(!filePath.startsWith('/')) {
        filePath = pwd() + "/${filePath}"
    }
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: Constants.nexusCredId,
                      usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD']]) {

        upload(new File(filePath), repo + group, versionedName, env.NEXUS_USER, env.NEXUS_PASSWORD)
    }
}

@Grab('io.github.http-builder-ng:http-builder-ng-core:1.0.3')
import java.io.File
import groovyx.net.http.*
import static groovyx.net.http.MultipartContent.multipart

def upload(File file, String repoPath, String name) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: Constants.nexusCredId,
                      usernameVariable: 'NEXUS_USERNAME', passwordVariable: 'NEXUS_PASSWORD']]) {
        HttpBuilder.configure {
            request.uri = Constants.nexusUrl
            request.auth.basic env.NEXUS_USERNAME, env.NEXUS_PASSWORD
        }.put {
            request.uri.path = "/repository/${repoPath}/${name}"
            request.contentType = 'multipart/form-data'
            request.body = multipart {
                part '', '', 'application/octet-stream', file
            }
            request.encoder 'multipart/form-data', CoreEncoders.&multipart
        }
        println("${file} uploaded to ${Constants.nexusUrl}/repository/${repoPath}/${name}")
    }
}
def call(Map args = [:]) {
    assert args.file : 'No file provided'
    def repo = args.repo ?: Constants.nexusDefaultRawRepo
    def group = args.group ? "/${args.group}" : ''
    def version = args.version ?: 'latest'
    def (name, ext) = args.file.split("/")[-1].split("\\.", 2) as List
    def versionedName = name + "-${version}." + ext
    upload(new File(args.file), repo + group, versionedName)
}

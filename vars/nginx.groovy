@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')
import groovyx.net.http.RESTClient
import groovy.json.JsonOutput
import static groovyx.net.http.ContentType.URLENC

def getActive(String apipath, String username, String password) {
    def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic username, password
    def hms = nginx.get(path: apipath).data
    (hms.available - hms.inactive)[0]
}

def getInactive(String apipath, String username, String password) {
    check(apipath, username, password)
    def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic username, password
    def hms = nginx.get(path: apipath).data
    (hms.available - hms.active)[0]
}

def check(String apipath, String username, String password) {
    def nginx1 = new RESTClient(Constants.nginx1ApiUrl)
    nginx1.auth.basic username, password
    def hms1 = nginx1.get(path: apipath).data
    hms1.inactive = (hms1.available - hms1.active)[0]

    def nginx2 = new RESTClient(Constants.nginx2ApiUrl)
    nginx2.auth.basic username, password
    def hms2 = nginx2.get(path: apipath).data
    hms2.inactive = (hms2.available - hms2.active)[0]

    if (hms1.inactive != hms2.inactive) {
        error "Inactive stacks mismatch on nginx1/2"
    }
}

def request(String apipath, String username, String password) {
    check(apipath, username, password)
    json = JsonOutput.toJson([setActive: getInactive(apipath, username, password)])

    def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic username, password

    def resp = nginx.post(
            path: apipath,
            body: json,
            requestContentType: URLENC)

    assert resp.status == 200

    nginx = new RESTClient(Constants.nginx2ApiUrl)
    nginx.auth.basic username, password

    resp = nginx.post(
            path: apipath,
            body: json,
            requestContentType: URLENC)

    assert resp.status == 200

    check(apipath, username, password)
}

def Switch(String apipath) {
    withCredentials([usernamePassword(credentialsId: 'nginx-auth-pass',
                                      passwordVariable: 'password',
                                      usernameVariable: 'username')]) {
        request(apipath, username, password)
    }
}

def Inactive(String apipath) {
    withCredentials([usernamePassword(credentialsId: 'nginx-auth-pass',
                                      passwordVariable: 'password',
                                      usernameVariable: 'username')]) {
        getInactive(apipath, username, password)
    }
}

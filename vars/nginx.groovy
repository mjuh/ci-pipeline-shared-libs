@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')
import groovyx.net.http.RESTClient
import groovy.json.JsonOutput
import static groovyx.net.http.ContentType.URLENC

def getActive(String apipath) {
    def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass
    def hms = nginx.get(path: apipath).data
    (hms.available - hms.inactive)[0]
}

def getInactive(String apipath) {
    check(apipath)
    def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass
    def hms = nginx.get(path: apipath).data
    (hms.available - hms.active)[0]
}

def check(String apipath) {
    def nginx1 = new RESTClient(Constants.nginx1ApiUrl)
    nginx1.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass
    def hms1 = nginx1.get(path: apipath).data
    hms1.inactive = (hms1.available - hms1.active)[0]

    def nginx2 = new RESTClient(Constants.nginx2ApiUrl)
    nginx2.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass
    def hms2 = nginx2.get(path: apipath).data
    hms2.inactive = (hms2.available - hms2.active)[0]

    if (hms1.inactive != hms2.inactive) {
        error "Inactive stacks mismatch on nginx1/2"
    }
}

def Switch(String apipath) {
    check(apipath)
    json = JsonOutput.toJson([setActive: getInactive(apipath)])

    def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass

    // def resp = nginx.post(
    //         path: apipath,
    //         body: json,
    //         requestContentType: URLENC)

    // assert resp.status == 200

    println(nginx.get(path: apipath, requestContentType: URLENC).data)

    println(nginx.dump())

    println(credentials('nginx-auth-pass').dump())

    withCredentials([
        usernamePassword(credentialsId: 'nginx-auth-pass',
                         usernameVariable: 'username',
                         passwordVariable: 'password')
    ]) {
        print 'username=' + username + 'password=' + password
        print 'username.collect { it }=' + username.collect { it }
        print 'password.collect { it }=' + password.collect { it }
    }

    nginx = new RESTClient(Constants.nginx2ApiUrl)
    nginx.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass

    // resp = nginx.post(
    //         path: apipath,
    //         body: json,
    //         requestContentType: URLENC)

    // assert resp.status == 200

    check(apipath)
}


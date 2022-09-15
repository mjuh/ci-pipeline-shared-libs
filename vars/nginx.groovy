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

def Request(String apipath, String username, String password) {
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
    // println(credentials('nginx-auth-pass').dump())
    nginx = new RESTClient(Constants.nginx2ApiUrl)
    nginx.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass
    // resp = nginx.post(
    //         path: apipath,
    //         body: json,
    //         requestContentType: URLENC)
    // assert resp.status == 200
    check(apipath)
    println("foo")
    println(username)
    println(password)
}

def Switch(String apipath) { 
    // define the secrets and the env variables
    // engine version can be defined on secret, job, folder or global.
    // the default is engine version 2 unless otherwise specified globally.
    def secrets = [[path: 'secret/vaultPass/majordomo/nginx1.intr',
                    engineVersion: 2,
                    secretValues: [[vaultKey: 'username'],
                                   [vaultKey: 'password']]]]
    // inside this block your credentials will be available as env variables
    withVault([vaultSecrets: secrets]) {
        Request(apipath, username, password)        
        // println(password)
    }
}

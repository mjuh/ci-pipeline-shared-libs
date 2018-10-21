@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')
import groovyx.net.http.RESTClient

def getInactive(String apipath) {
  def nginx = new RESTClient(Constants.nginx1ApiUrl)
    nginx.auth.basic Constants.nginxAuthUser, Constants.nginxAuthPass
  def hms = nginx.get(path: apipath).data
    hms.inactive = (hms.available - hms.active)[0]
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


//TODO:
//def getAll() {
//def nginx = new RESTClient('http://nginx1.intr:8080')
//nginx.auth.basic 'jenkins', '***REMOVED***'
//def hms = nginx.get(path: '/hms').data
//}
//def switch() {}

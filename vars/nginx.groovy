@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7')
import groovyx.net.http.RESTClient

def getInactive() {
  def nginx = new RESTClient('http://nginx1.intr:8080')
    nginx.auth.basic 'jenkins', 'jenkins4nginx'
  def hms = nginx.get(path: '/hms').data
    hms.inactive = (hms.available - hms.active)[0]
}

def check() {
  def nginx1 = new RESTClient('http://nginx1.intr:8080')
    nginx1.auth.basic 'jenkins', 'jenkins4nginx'
  def hms1 = nginx1.get(path: '/hms').data
    hms1.inactive = (hms1.available - hms1.active)[0]

  def nginx2 = new RESTClient('http://nginx2.intr:8080')
    nginx2.auth.basic 'jenkins', 'jenkins4nginx'
  def hms2 = nginx2.get(path: '/hms').data
    hms2.inactive = (hms2.available - hms2.active)[0]

  if (hms1.inactive != hms2.inactive) {
    error "Inactive stacks mismatch on nginx1/2"
  }

}


//TODO:
//def getAll() {
//def nginx = new RESTClient('http://nginx1.intr:8080')
//nginx.auth.basic 'jenkins', 'jenkins4nginx'
//def hms = nginx.get(path: '/hms').data
//}
//def switch() {}

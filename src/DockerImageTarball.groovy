import groovy.json.JsonSlurperClassic

class DockerImageTarball {
    private String fqImageName
    public String path

    DockerImageTarball(String path, String imageName = '') {
        this.path = new File(path).getCanonicalPath()
        this.fqImageName = imageName ?: this.getManifest().RepoTags[0]
    }

    private getManifest() {
        def tmpPath = '/tmp' + (this.path.split('/')[-1]).split('\\.')[0]
        def tmp = new File(tmpPath)
        tmp.mkdirs()
        "tar xzf ${this.path} manifest.json".execute(null, tmp)
        def manifestFile = new File(tmpPath + 'manifest.json')
        def manifest = (new groovy.json.JsonSlurperClassic().parseText(manifestFile.getText()))[0]
        manifestFile.delete()
        tmp.delete()
        manifest
    }

    public imageName() {
        this.fqImageName
    }

    public setImageName(String imageName) {
        this.fqImageName = imageName
    }

    public getTag() {
        this.fqImageName.split(':')[-1]
    }

    public tag(String tag) {
        this.fqImageName = this.fqImageName.split(':')[0..-2].join() + ":${tag}"
    }
}

def call(Map args = [:]) {
    if(!args.image) {
        assert args.name: 'No image provided'
        assert args.namespace: "No namespace provided"
    }
    def registry = args.registry ?: Constants.dockerRegistryHost
    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def imageName = args.image ? args.image.imageName() : "${registry}/${args.namespace}/${args.name}:${tag}"
    def containerStructureTestImage = args.containerStructureTestImage ?: Constants.containerStructureTestImage
    def config = args.configFileName ?: 'container-structure-test.yaml'
    assert config.endsWith('.yaml') || config.endsWith('.json') : 'Supported config file extensions are .yaml and .json'
    def configOnHost = "${env.WORKSPACE}" + '/' + config

    dockerRun(volumes: [(configOnHost): "/${config}",
                        '/var/run/docker.sock': '/var/run/docker.sock'],
              image: containerStructureTestImage,
              cmd: "test --image ${imageName} --config ${config}",
              uid: '0')
}

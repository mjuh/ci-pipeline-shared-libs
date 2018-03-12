def call(Map args = [:]) {
    assert args.image : 'No image provided'
    assert args.namespace : "No namespace provided"

    def registry = args.registry ?: Constants.dockerRegistryHost
    def tag = args.tag ?: Constants.dockerImageDefaultTag
    def image = "${registry}/${args.namespace}/${args.image}:${tag}"
    def containerStructureTestImage = args.containerStructureTestImage ?: Constants.containerStructureTestImage
    def config = args.configFileName ?: 'container-structure-test.yaml'
    assert config.endsWith('.yaml') || config.endsWith('.json') : 'Supported config file extensions are .yaml and .json'
    def configOnHost = new JenkinsContainer().getHostPath(env.WORKSPACE + '/' + config)

    dockerRun(volumes: [(configOnHost): "/${config}",
                        '/var/run/docker.sock': '/var/run/docker.sock'],
              image: containerStructureTestImage,
              name: "containert-structure-test-${env.BUILD_TAG}",
              cmd: "-image ${image} ${config}")
}

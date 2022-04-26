class DockerImageTarball implements Serializable {
    String imageName, path;
    public String imageName() {
	return this.imageName.split("/").drop(1).join("/").split(":")[0];
    }
}

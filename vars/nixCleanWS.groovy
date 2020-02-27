def call(Map args = [:]) {
    Number mtime = args.mtime ?: 7
    String directory = args.directory ?: "${env.WORKSPACE}/result"
    List<String> toDelete = "find $directory -type l ! -mtime -$mtime".execute().text.trim().split("\n")
    if (!toDelete.isEmpty()) {
        sh (toDelete.collect{"rm -f $it"}.join("; "))
    }
}

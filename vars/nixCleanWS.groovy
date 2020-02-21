def call(Map args = [:]) {
    Number mtime = args.mtime ?: 7
    List<String> toDelete = "find ${WORKSPACE}/result -type l ! -mtime -$mtime".execute().text.trim().split("\n")
    if (toDelete) {
        sh (toDelete.collect{"rm -f $it"}.join("; "))
    }
}

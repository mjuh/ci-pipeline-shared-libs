def call(Map args = [:]) {
    assert args.users : 'No users provided.'

    def passwdPath = args.passwdPath ?: 'passwd'
    def groupPath = args.groupPath ?: 'group'
    def passwdBody = ''
    def groupBody =''

    args.users.each { user, v ->
        assert v.uid : "${user} user has no UID."
        passwdBody += "${user}:x:${v.uid}:${v.gid ?: v.uid}:,,,,:${v.home}:${v.shell ?: '/bin/sh'}\n"
        if(args.groups && !args.groups."${user}") {
            groupBody += "${user}:x:${v.gid ?: v.uid}:${user}\n"
        }
    }
    args.groups.each { group, v ->
        assert v.gid : "${group} has no GID."
        assert v.users : "${group} has no users"
        groupBody += "${group}:x:${v.gid}:${v.users.join(',')}\n"
    }

    writeFile(file: passwdPath, text: passwdBody)
    writeFile(file: groupPath, text: groupBody)
}

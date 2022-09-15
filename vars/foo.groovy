def call() {
    // define the secrets and the env variables
    // engine version can be defined on secret, job, folder or global.
    // the default is engine version 2 unless otherwise specified globally.
    def secrets = [[path: 'secret/vaultPass/majordomo/nginx1.intr',
                    engineVersion: 2,
                    secretValues: [[vaultKey: 'username'],
                                   [vaultKey: 'password']]]]

    // inside this block your credentials will be available as env variables
    withVault([vaultSecrets: secrets]) {
        println(username)
        println(password)
    }
}
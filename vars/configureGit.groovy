def call(Map args = [:]) {
    createSshDirWithGitKey(dir: HOME + '/.ssh')
    sh """
          git config --global user.name 'jenkins'
          git config --global user.email 'jenkins@majordomo.ru'
          git stash
       """
}

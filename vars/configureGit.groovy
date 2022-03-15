def call(Map args = [:]) {
    sh """
          git config --global user.name 'jenkins'
          git config --global user.email 'jenkins@majordomo.ru'
       """
}

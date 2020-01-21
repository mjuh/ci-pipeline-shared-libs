def call(msg, color = 'green') {
    def rgb = [green: '#00ff00',
               yellow: '#ffff00',
               red: '#ff0000',
               good: '#00ff00',
               bad: '#ff0000'][color.toLowerCase()]
    slackSend(color: rgb, message: msg)
}

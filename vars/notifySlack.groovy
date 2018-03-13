def call(msg, color = 'green') {
    def rgb = [green: '#00ff00',
               yellow: '#ffff00',
               red: '#ff0000',
               good: '#00ff00',
               bad: '#ff0000'][color.toLowerCase()]
    slackSend(botUser: true,
              channel: Constants.slackChannel,
              color: rgb,
              message: msg,
              teamDomain: Constants.slackTeam,
              token: Constants.slackToken)
}
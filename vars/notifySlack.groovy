def call(msg, status = 'good') {
    slackSend(botUser: true,
              channel: Constants.slackChannel,
              color: status,
              message: msg,
              teamDomain: Constants.slackTeam,
              token: Constants.slackToken)
}
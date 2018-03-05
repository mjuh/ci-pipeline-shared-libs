def call(Map args) {
	def gitCredId = args.gitCredId ?: Constants.gitCredId
	def gitHost = args.gitHost ?: Constants.gitHost
	def localUsername = args.localUsername ?: 'jenkins'
	def localHomedir = args.localHomedir ?: env.HOME
	def dir = args.dir ?: '.ssh'
	def sshWrapperFilename = args.sshWrapperFilename ?: 'wrap-ssh4git.sh'

	sh "mkdir -p -m 700 ${dir}"

//	withCredentials([[$class: 'sshUserPrivateKey', credentialsId: gitCredId,
//                      usernameVariable: 'USERNAME', keyFileVariable: 'KEY_FILE']]) {
//		writeFile(
//			file: dir + '/config',
//			text: """
//				Host ${gitHost}
//				User $USERNAME
//				HostName ${gitHost}
//				IdentityFile ${localHomedir}/${dir}/git_repos_deploy_key
//			"""
//		)
//		writeFile(
//			file: dir + '/' + sshWrapperFilename,
//			text: """
//				#!/bin/sh
//				/usr/bin/env ssh -o 'StrictHostKeyChecking=no' -i '${localHomedir}/${dir}/git_repos_deploy_key' $1 $2
//			"""
//		)
//		sh """
//			chmod +x ${dir}/${sshWrapperFilename}
//			cp $KEY_FILE ${dir}/git_repos_deploy_key
//			chmod 400 ${dir}/git_repos_deploy_key
//		"""
//	}
}

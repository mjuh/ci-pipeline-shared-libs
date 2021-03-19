# Jenkins

Load shared library from another branch

``` shell
@Library('mj-shared-library@another-git-branch') _
```

Stop job clicker:

``` shell
while true; do xdotool click 1; xdotool key Return; sleep 0.5; done
```

# GitLab

[https://gitlab.intr/webservices/webftp-new/pipelines?scope=running&page=1](https://gitlab.intr/webservices/webftp-new/pipelines?scope=running&page=1 "Cancel Pipeline Running jobs")
``` shell
while true; do (eval $(xdotool getmouselocation --shell); xdotool click 1; xdotool mousemove 1100 380; xdotool click 1; xdotool mousemove $X $Y); sleep 0.5; done
```

## gitlab-plugin

https://github.com/jenkinsci/gitlab-plugin
```
00:00:02.402  Failed to update Gitlab commit status for project 'nixos/ns': HTTP 403 Forbidden
```
Give this user 'Maintainer' permissions on each repo you want Jenkins to send build status to

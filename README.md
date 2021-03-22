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


Update existing groups:
``` bash
root@dh4-mr ~ # docker exec -it -u 996 ci_gitlab.1.upjyyidg1if62dde7bw24bd48 bash
gitlab-psql@gitlab:/$ gitlab-psql psql -h /var/opt/gitlab/postgresql -U gitlab-psql -d gitlabhq_production
```

```
gitlabhq_production=# select * from users where id=14;
gitlabhq_production=# select * from members where user_id=14 and type='GroupMember';

gitlabhq_production=# update members set access_level=40 where user_id=14 and type='GroupMember';
UPDATE 22
```

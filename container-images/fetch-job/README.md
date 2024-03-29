# FetchJob container image

Container to run a job that fetches from remote repositories to corresponding
local repositories. Thi can be used to create copies of repositories hosted on
other git servers in the local Gerrit. This is useful for backing up repositories
or to have a copy of branches of a project that was forked in the local Gerrit.

## Content

* base image
* `fetch-job.sh`: script executing the fetches

## Start

*  execution of the provided `fetch-job.sh`

## Configuration

```yaml
remotes:
- name: example
  url: https://example.com
  timeout: 5m
  fetch:
  - remoteRepo: project1
  - remoteRepo: project2
    localRepo: local/project2
  - remoteRepo: project3
    localRepo: local/project3
    refSpec: "+refs/heads/*:refs/heads/remote/*"
```

You will need to mount the credentials used to authenticate with remote servers
to the container, e.g. by providing a .netrc file at `home/gerrit/.netrc`.

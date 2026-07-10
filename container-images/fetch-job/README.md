# FetchJob container image

Container to run a job that fetches from remote repositories to corresponding
local repositories. This can be used to create copies of repositories hosted on
other git servers in the local Gerrit. This is useful for backing up repositories
or to have a copy of branches of a project that was forked in the local Gerrit.

## Content

* base image
* `fetch-job.py`: script executing the fetches

## Start

*  execution of the provided `fetch-job.py`

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
  - remoteRepo: project4
    localRepo: local/project4
    refSpec:
      - +refs/heads/*:refs/heads/remote/*
      - ^refs/heads/excluded-*
```

## Authentication

Mount a `.netrc` file at `/home/gerrit/.netrc` to provide credentials for
authenticating with remote servers.

## TLS/SSL

If the remote server uses a certificate signed by a non-publicly-trusted CA
(e.g. an internal CA), provide the CA certificate in PEM format at
`/home/gerrit/ca.crt`. The fetch-job will automatically use it for all fetch
operations if present.

When using the Gerrit operator, both the `.netrc` and `ca.crt` files can be
provided via the `secretRef` field of the `IncomingReplicationTask` resource.
If the referenced secret contains a `.netrc` key and/or a `ca.crt` key, the
operator will mount them at the expected paths automatically.
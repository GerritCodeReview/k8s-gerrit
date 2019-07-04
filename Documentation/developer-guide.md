# Developer Guide

[TOC]

## Code Review

This project uses Gerrit for code review:
https://gerrit-review.googlesource.com/
which uses the ["git push" workflow][1] with server
https://gerrit.googlesource.com/k8s-gerrit. You will need a
[generated cookie][2].

Gerrit depends on "Change-Id" annotations in your commit message.
If you try to push a commit without one, it will explain how to
install the proper git-hook:

```
curl -Lo `git rev-parse --git-dir`/hooks/commit-msg \
    https://gerrit-review.googlesource.com/tools/hooks/commit-msg
chmod +x `git rev-parse --git-dir`/hooks/commit-msg
```

Before you create your local commit (which you'll push to Gerrit)
you will need to set your email to match your Gerrit account:

```
git config --local --add user.email foo@bar.com
```

Normally you will create code reviews by pushing for master:

```
git push origin HEAD:refs/for/master
```

## Developing container images

When changing or creating container images, keep the image size as small as
possible. This reduces storage space needed for images, the upload time and most
importantly the download time, which improves startup time of pods.

Some good practices are listed here:

- **Chain commands:** Each `RUN`-command creates a new layer in the docker image.
Each layer increases the total image size. Thus, reducing the number of layers,
can also reduce the image size.

- **Clean up after package installation:** The package installation creates a
number of cache files, which should be removed after installation. In Ubuntu/Debian-
based images use the following snippet (This requires `apt-get update` before
each package installation!):

```docker
RUN apt-get update && \
    apt get install -y <packages> && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
```

In Alpine based images use the `--no-cache`-flag of `apk`.

- **Clean up temporary files immediately:** If temporary files are created by a
command remove them in the same command chain.

- **Use multi stage builds:** If some complicated build processes are needed for
building parts of the container image, of which only the final product is needed,
use [multi stage builds][3]


[1]: https://gerrit-review.googlesource.com/Documentation/user-upload.html#_git_push
[2]: https://gerrit.googlesource.com/new-password
[3]: https://docs.docker.com/develop/develop-images/multistage-build/

## Writing clean python code

When writing python code, either for tests or for scripts, use `black` and `pylint`
to ensure a clean code style. They can be run by the following commands:

```sh
pipenv install --dev
pipenv run black $(find . -name '*.py')
pipenv run pylint $(find . -name '*.py')
```

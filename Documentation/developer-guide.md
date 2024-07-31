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

Container images used by the Gerrit deployment are being assembled in Bazel using
[apko](https://github.com/chainguard-dev/apko) to provide alpine-based base images
and [rules_oci](https://github.com/bazel-contrib/rules_oci) to build OCI-conform
images.

The process necessary to change or create such an image will be described in the
following:

### Update apko locks

Apko creates lock files to ensure that the same package versions etc. are used
in every build.

To update the lock files, run:

```sh
bazelisk run //container-images/apache-git-http-backend:lock
bazelisk run //container-images/fetch-job:lock
bazelisk run //container-images/gerrit:lock
bazelisk run //container-images/gerrit-init:lock
bazelisk run //container-images/git-gc:lock
```

Since this also changes the bazel module, its lock has to be updated as well:

```sh
bazelisk mod deps --lockfile_mode=update
```

### Update Alpline version

All container images use the base configuration maintained in
`container-images/base/apko.part.yaml`. To update the Alpine version, set it
in this file.

If necessary, also update the repository URLs in the `apko.part.yaml` files under
`contents.repositories` to use a repository for the new version.

Afterwards, the locks for all container images have to be updated as described
[here](#update-apko-locks).

### Installing OS packages

OS packages are also managed by apko. To install a ew package add it in the
respective `apko.part.yaml` under `contents.packages`. `contents.repositories`
might have to be adapted, if the package is not in the main repository.

Afterwards, the locks for all container images have to be updated as described
[here](#update-apko-locks).

### Additional changes to the base images

To find more information about adding environment variables, creating directories
etc. refer to the [apko documentation](https://github.com/chainguard-dev/apko/blob/main/docs/apko_file.md).

### Update Gerrit and its plugins

The Gerrit version can be changed by changing the Gerrit URL in `MODULE.bazel`.
The same is true for the built-in plugins. Afterwards, the Bazel module lock
has to be updated.

### Adding a new script/artifact

To add a new script or some build output of this repository to a container image,
it has to be packaged as a tar file, which will be used as a layer. This should
be done using the [`pkg_tar`-rule](https://github.com/bazelbuild/rules_pkg/blob/main/docs/1.0.1/reference.md#pkg_tar).
The new layer has than to be referenced in the `oci_image`-rule's `tar` parameter
to be added to the container image.
There are a lot of examples on how to do that in this repository.

### Creating a new container image

To do that create a new subdirectory under `container-images` and create the
`BUILD.bazel` and `apko.part.yaml` files. Each image should inherit from the
base configuration. This is done by merging the base configuration with the
specific configuration. To do that, add this snippet to the `BAZEL.build`-file:

```python
load("@aspect_bazel_lib//lib:yq.bzl", "yq")

yq(
    name = "merge-base",
    srcs = [
        "apko.part.yaml",
        "//container-images/base:apko.part.yaml",
    ],
    outs = ["apko.yaml"],
    expression = ". as $item ireduce ({}; . *+ $item )",
)
```

To create the apko base image the following snippets have to be added as well
(adapt the container image name as required):

```python
load("@rules_apko//apko:defs.bzl", "apko_image", "apko_lock")

apko_lock(
    name = "lock",
    config = ":merge-base",
    lockfile_name = "apko.lock.json",
)

apko_image(
    name = "apko-gerrit-init",
    architecture = select({
        "@platforms//cpu:arm64": "arm64",
        "@platforms//cpu:x86_64": "amd64",
    }),
    config = "apko.yaml",
    contents = "@gerrit-init_lock//:contents",
    tag = "gerrit-init:latest",
)
```

Afterwards, fill in the apko-configuration in `apko.part.yaml` and then lock it:

```sh
bazelisk run //container-images/gerrit-init:lock
```

Then update the bazel Module by adding the locked content as a repository. To do
that, add the following snippet to `MODULE.bazel` (replacing container/target
names):

```python
apk.translate_lock(
    name = "gerrit-init_lock",
    lock = "//container-images/gerrit-init:apko.lock.json",
)
use_repo(apk, "gerrit-init_lock")
```

And then run:

```sh
bazelisk mod deps --lockfile_mode=update
```

To set up the build for the container image, use the following snippet in the
container image's `BUILD.bazel`:

```python
oci_image(
    name = "gerrit-init",
    base = ":apko-gerrit-init",
    entrypoint = [
        # entrypoint command,
    ],
    tars = [
        # tar file targets containing the layers
    ],
)

oci_tarball(
    name = "build",
    image = ":gerrit-init",
    repo_tags = ["gerrit-init:latest"],
    visibility = ["//visibility:public"],
)

oci_push(
    name = "publish",
    image = ":gerrit-init",
    repository = "docker.io/k8sgerrit/gerrit-init",
    remote_tags = "//:full-version",
    visibility = ["//visibility:public"],
)
```

Finally, add the new container image targets to the rules to build and
publish all container images in the root `BUILD.bazel` file

## Writing clean python code

When writing python code, either for tests or for scripts, use `black` and `pylint`
to ensure a clean code style. They can be run by the following commands:

```sh
pipenv install --dev
pipenv run black $(find . -name '*.py')
pipenv run pylint $(find . -name '*.py')
```

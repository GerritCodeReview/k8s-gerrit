# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

usage() {
    me=`basename "$0"`
    echo >&2 "Usage: $me [--help] [--output (K8SGERRIT | GERRIT | COMBINED)] [--platform PLATFORM] [--gerrit-war-url GERRIT_WAR_URL] [--no-docker]"
    exit 1
}

OUTPUT="COMBINED"
USE_DOCKER=true

while test $# -gt 0 ; do
  case "$1" in
  --help)
    usage
    ;;

  --output)
    shift
    OUTPUT=$1
    shift
    ;;

  --platform)
    shift
    PLATFORM=$1
    shift
    ;;

  --no-docker)
    USE_DOCKER=false
    shift
    ;;

  --gerrit-url)
    shift
    GERRIT_WAR_URL=$1
    shift
    ;;

  *)
    break
  esac
done

getK8sVersion() {
    echo "$(git describe --always --dirty --abbrev=10)"
}

getGerritVersionDocker() {
    PLATFORM=${PLATFORM:-linux/amd64}  # Default value if PLATFORM is not set
    GERRIT_VERSION="$(
        docker run \
            --platform=$PLATFORM \
            --entrypoint '/bin/sh' \
            gerrit-base:$(getK8sVersion) \
            -c 'java -jar /var/gerrit/bin/gerrit.war version'
        )"
    GERRIT_VERSION="$(
        echo "${GERRIT_VERSION##*$'\n'}" \
        | cut -d' ' -f3 \
        | tr -d '[:space:]'
    )"
    echo "$GERRIT_VERSION"
}

getGerritVersionNoDocker() {
    GERRIT_BRANCH=${GERRIT_BRANCH:-main}
    GERRIT_WAR_URL=${GERRIT_WAR_URL:-https://git-ci.dev.od.sap.biz/view/Gerrit/job/Gerrit-bazel-gcp-${GERRIT_BRANCH}/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war}

    # Create temporary directory for WAR file
    TMP_DIR="./dist"
	mkdir -p $TMP_DIR

    GERRIT_WAR_FILE="$TMP_DIR/gerrit.war"

    if ! test -f "$GERRIT_WAR_FILE"; then
        if ! curl -f -k -s -S -o "$GERRIT_WAR_FILE" "$GERRIT_WAR_URL" 2>&1 >&2; then
            echo "Error: Failed to download Gerrit WAR file from $GERRIT_WAR_URL" >&2
            exit 1
        fi
    fi

    GERRIT_VERSION="$(java -jar "$GERRIT_WAR_FILE" version | cut -d' ' -f3)"

    if [ -z "$GERRIT_VERSION" ]; then
        echo "Error: Could not extract Gerrit version from WAR file" >&2
        exit 1
    fi

    echo "$GERRIT_VERSION"
}

getGerritVersion() {
    if [ "$USE_DOCKER" = "true" ]; then
        getGerritVersionDocker
    else
        getGerritVersionNoDocker
    fi
}

case $OUTPUT in
    K8SGERRIT)
        echo "$(getK8sVersion)"
        ;;
    GERRIT)
        echo "$(getGerritVersion)"
        ;;
    COMBINED)
        echo "$(getK8sVersion)-$(getGerritVersion)"
        ;;
    *)
        usage
        ;;
esac

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
    echo >&2 "Usage: $me [--help] [--output (K8SGERRIT | GERRIT | COMBINED)]"
    exit 1
}

OUTPUT="COMBINED"

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

  *)
    break
  esac
done

getK8sVersion() {
    echo "$(git describe --always --dirty --abbrev=10)"
}

getGerritVersion() {
    GERRIT_VERSION="$(
        docker run \
            --platform=linux/amd64 \
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

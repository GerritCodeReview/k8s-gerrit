#!/bin/ash
# Copyright (C) 2011, 2024 SAP SE
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

usage() {
    me=`basename "$0"`
    echo >&2 "Usage: $me [--config CONFIG_FILE]"
    exit 1
}

GIT_HOME=/var/gerrit/git
CONFIG_FILE=/var/gerrit/etc/incoming-replication.config.yaml

while test $# -gt 0 ; do
  case "$1" in
  --help)
    usage
    ;;

  --config)
    shift
    CONFIG_FILE=$1
    shift
    ;;

  *)
    break
  esac
done

if !test -f $CONFIG_FILE; then
  echo "No config file has been found."
  usage
fi

for REMOTE in $(yq e '.remotes[]' "$CONFIG_FILE"); do
  REMOTE_NAME="$(echo "$REMOTE" | yq e '.name' -)"
  REMOTE_SERVER_URL="$(echo "$REMOTE" | yq e '.url' -)"
  REMOTE_TIMEOUT="$(echo "$REMOTE" | yq e '.timeout' -)"

  for FETCH in $(echo "$REMOTE" | yq e '.fetch[]' -); do
    REMOTE_REPO="$(echo \"$FETCH\" | yq e '.remoteRepo' -)"

    LOCAL_REPO="$(echo \"$FETCH\" | yq e '.localRepo' -)"
    test -n "$LOCAL_REPO" || LOCAL_REPO="$REMOTE_REPO"

    REFSPEC="$(echo \"$FETCH\" | yq e '.refSpec' -)"
    test -n "$REFSPEC" || REFSPEC="+refs/heads/*:refs/heads/$REMOTE_NAME/*"

    REMOTE_URL="$REMOTE_SERVER_URL/$REMOTE_REPO"

    GIT_DIR="--git-dir=$GIT_HOME/$LOCAL_PROJECT.git"
    git $GIT_DIR config remote.$REMOTE_NAME.url $REMOTE_URL
    git $GIT_DIR config --unset-all remote.$REMOTE_NAME.fetch
    git $GIT_DIR config remote.$REMOTE_NAME.fetch $REFSPEC

    timeout $REMOTE_TIMEOUT git $GIT_DIR fetch $REMOTE_NAME
  done
done

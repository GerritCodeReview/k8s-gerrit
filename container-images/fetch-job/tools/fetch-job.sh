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

if ! test -f $CONFIG_FILE; then
  echo "No config file has been found."
  usage
fi

NUM_REMOTES=$(yq e '.remotes | length' "$CONFIG_FILE")
for i in $(seq 0 $((NUM_REMOTES-1))); do
  REMOTE=$(NUM_REMOTE=$i yq e '.remotes[env(NUM_REMOTE)]' "$CONFIG_FILE")
  REMOTE_NAME="$(echo "$REMOTE" | yq e '.name' -)"
  REMOTE_SERVER_URL="$(echo "$REMOTE" | yq e '.url' -)"
  REMOTE_TIMEOUT="$(echo "$REMOTE" | yq e '.timeout' -)"

  NUM_FETCHES=$(echo "$REMOTE" | yq e '.fetch | length' -)
  for j in $(seq 0 $((NUM_FETCHES-1))); do
    FETCH=$(echo "$REMOTE" | NUM_FETCH=$j yq e '.fetch[env(NUM_FETCH)]' -)
    REMOTE_REPO="$(echo "$FETCH" | yq e '.remoteRepo' -)"

    LOCAL_REPO="$(echo "$FETCH" | yq e '.localRepo' -)"
    (test -z "$LOCAL_REPO" || [ "$LOCAL_REPO" = "null" ]) && LOCAL_REPO="$REMOTE_REPO"

    REFSPEC="$(echo "$FETCH" | yq e '.refSpec' -)"
    (test -z "$REFSPEC" || [ "$REFSPEC" = "null" ]) && REFSPEC="+refs/heads/*:refs/heads/$REMOTE_NAME/*"

    REMOTE_URL="$REMOTE_SERVER_URL/$REMOTE_REPO"

    GIT_DIR="--git-dir=$GIT_HOME/$LOCAL_REPO.git"
    git $GIT_DIR config remote.$REMOTE_NAME.url $REMOTE_URL
    git $GIT_DIR config --unset-all remote.$REMOTE_NAME.fetch
    git $GIT_DIR config remote.$REMOTE_NAME.fetch $REFSPEC

    echo "Fetching $REMOTE_REPO from $REMOTE_NAME"
    timeout $REMOTE_TIMEOUT git $GIT_DIR fetch $REMOTE_NAME
  done
done

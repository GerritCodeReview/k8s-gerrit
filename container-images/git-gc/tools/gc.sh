#!/bin/ash
# Copyright (C) 2011, 2020 SAP SE
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

usage()
{
  echo "Usage: $0 [ -s ProjectName ] [ -p ProjectName ] [ -b ProjectName ]"
  echo "-s ProjectName     : skip this project"
  echo "-p ProjectName     : run git-gc for this project"
  echo "-B                 : do not write bitmaps"
  echo "-R                 : skip packing refs, use this to reduce lock contention"
  echo "                     on packed-refs which is frequently locked by Gerrit"
  echo "                     packed-refs updates on busy repos"
  echo "-P                 : preserve packs"
  echo ""
  echo "By default the script will run git-gc for all projects unless \"-p\" option is provided"
  echo
  echo "Examples:"
  echo "  Run git-gc for all projects but skip foo and bar/baz projects"
  echo "    $0 -s foo -s bar/baz"
  echo "  Run git-gc only for foo and bar/baz projects"
  echo "    $0 -p foo -p bar/baz"
  echo "  Run git-gc only for bar project without writing bitmaps"
  echo "    $0 -p bar -b bar"
  echo
  echo "To specify a one-time --aggressive git gc for a repository X, simply"
  echo "create an empty file called \'gc-aggressive-once\' in the \$SITE/git/X.git"
  echo "folder:"
  echo
  echo "  \$ cd \$SITE/git/X.git"
  echo "  \$ touch gc-aggressive-once"
  echo
  echo "On the next run, gc.sh will use --aggressive option for gc-ing this"
  echo "repository *and* will remove this file. Next time, gc.sh again runs"
  echo "normal gc for this repository."
  echo
  echo "To specify a permanent --aggressive git gc for a repository, create"
  echo "an empty file named "gc-aggresssive" in the same folder:"
  echo
  echo "  \$ cd \$SITE/git/X.git"
  echo "  \$ touch gc-aggressive"
  echo
  echo "Every next git gc on this repository will use --aggressive option."
  exit 2
}

gc_options()
{
  if test -f "$1/gc-aggressive" ; then
    echo "--aggressive"
  elif test -f "$1/gc-aggressive-once" ; then
    echo "--aggressive"
    rm -f "$1/gc-aggressive-once"
  else
    echo ""
  fi
}

set_gc_command() {
  if [ $PRESERVE_PACKS_OPT -eq 0 ] ; then
    GC_COMMAND="gc"
  else
    GC_COMMAND="gc-preserve"
  fi
}

log_opts()
{
  if test -z $1 ; then
    echo ""
  else
    echo " [$1]"
  fi
}

log()
{
  # Rotate the $LOG if current date is different from the last modification of $LOG
  if test -f "$LOG" ; then
    TODAY=$(date +%Y-%m-%d)
    LOG_LAST_MODIFIED=$(date +%Y-%m-%d -r $LOG)
    if test "$TODAY" != "$LOG_LAST_MODIFIED" ; then
      mv "$LOG" "$LOG.$LOG_LAST_MODIFIED"
      gzip "$LOG.$LOG_LAST_MODIFIED"
    fi
  fi

  # Do not log an empty line
  if [[ ! "$1" =~ ^[[:space:]]*$ ]]; then
    echo $1
    echo $1 >>$LOG
  fi
}

gc_all_projects()
{
  find $TOP -type d -path "*.git" -prune -o -name "*.git" | while IFS= read d
  do
    gc_project "${d#$TOP/}"
  done
}

gc_specified_projects()
{
  for PROJECT_NAME in ${GC_PROJECTS}
  do
    gc_project "$PROJECT_NAME"
  done
}

gc_project()
{
  PROJECT_NAME="$@"
  PROJECT_DIR="$TOP/$PROJECT_NAME"

  if [[ ! -d "$PROJECT_DIR" ]]; then
    OUT=$(date +"%D %r Failed: Directory does not exist: $PROJECT_DIR") && log "$OUT"
    return 1
  fi

  OPTS=$(gc_options "$PROJECT_DIR")
  LOG_OPTS=$(log_opts $OPTS)

  # Check if git-gc for this project has to be skipped
  if [ $SKIP_PROJECTS_OPT -eq 1 ]; then
    for SKIP_PROJECT in ${SKIP_PROJECTS}; do
      if [ $SKIP_PROJECT == "$PROJECT_NAME" ] ; then
        OUT=$(date +"%D %r Skipped: $PROJECT_NAME") && log "$OUT"
        return 0
      fi
    done
  fi

  # Check if writing bitmaps for this project has to be disabled
  WRITEBITMAPS='true';
  if [ $DONOT_WRITE_BITMAPS_OPT -eq 1 ]; then
    WRITEBITMAPS='false';
  fi

  OUT=$(date +"%D %r Started: $PROJECT_NAME$LOG_OPTS") && log "$OUT"

  git --git-dir="$PROJECT_DIR" config core.logallrefupdates true

  git --git-dir="$PROJECT_DIR" config repack.usedeltabaseoffset true
  git --git-dir="$PROJECT_DIR" config repack.writebitmaps $WRITEBITMAPS
  git --git-dir="$PROJECT_DIR" config pack.compression 9
  git --git-dir="$PROJECT_DIR" config pack.indexversion 2

  git --git-dir="$PROJECT_DIR" config gc.autodetach false
  git --git-dir="$PROJECT_DIR" config gc.auto 0
  git --git-dir="$PROJECT_DIR" config gc.autopacklimit 0
  git --git-dir="$PROJECT_DIR" config gc.packrefs true
  git --git-dir="$PROJECT_DIR" config gc.reflogexpire never
  git --git-dir="$PROJECT_DIR" config gc.reflogexpireunreachable never
  git --git-dir="$PROJECT_DIR" config receive.autogc false

  git --git-dir="$PROJECT_DIR" config pack.window 250
  git --git-dir="$PROJECT_DIR" config pack.depth 50

  OUT=$(git $GC_CONFIG --git-dir="$PROJECT_DIR" "$GC_COMMAND" --auto --prune $OPTS \
        || date +"%D %r Failed: $PROJECT_NAME") \
    && log "$OUT"

  delete_empty_ref_dirs "$PROJECT_DIR"

  OUT=$(find "$PROJECT_DIR/objects" -name 'incoming_*.pack' -type f -mtime +14 -delete) && \
        log "pruning stale 'incoming_*.pack' files older than 14 days:\n$OUT"

  if [ $DONOT_PACK_REFS_OPT -eq 0 ] ; then
    local looseRefCount
    looseRefCount=$(find "$PROJECT_DIR/refs/" -type f | wc -l)
    if [ $looseRefCount -gt 10 ] ; then
      OUT=$(git --git-dir="$PROJECT_DIR" pack-refs --all) && \
          log "Found $looseRefCount loose refs -> pack all refs"
    fi
  fi

  OUT=$(date +"%D %r Finished: $PROJECT_NAME$LOG_OPTS") && log "$OUT"
}

delete_empty_ref_dirs()
{
  PROJECT_DIR="$1"
  find "$PROJECT_DIR/refs" -type d -empty -mindepth 2 -mmin +60 -delete
}

###########################
# Main script starts here #
###########################

SKIP_PROJECTS=
GC_PROJECTS=
SKIP_PROJECTS_OPT=0
GC_PROJECTS_OPT=0
DONOT_WRITE_BITMAPS_OPT=0
DONOT_PACK_REFS_OPT=0
PRESERVE_PACKS_OPT=0
# set auto gc options on the fly when git gc is triggered by cron
GC_CONFIG="-c gc.auto=6700 -c gc.autoPackLimit=4"

while getopts 's:p:BRP?h' c
do
  case $c in
    s)
      SKIP_PROJECTS="${SKIP_PROJECTS} ${OPTARG}.git"
      SKIP_PROJECTS_OPT=1
      ;;
    p)
      GC_PROJECTS="${GC_PROJECTS} ${OPTARG}.git"
      GC_PROJECTS_OPT=1
      ;;
    B)
      DONOT_WRITE_BITMAPS_OPT=1
      ;;
    R)
      GC_CONFIG="$GC_CONFIG -c gc.packRefs=false"
      DONOT_PACK_REFS_OPT=1
      ;;
    P)
      PRESERVE_PACKS_OPT=1
      ;;
    h|?|*)
      usage
      ;;
  esac
done

shift $(($OPTIND - 1))
test $# -gt 0 && usage

TOP=/var/gerrit/git
LOG=/var/log/git/gc.log
set_gc_command

OUT=$(date +"%D %r Started") && log "$OUT"

if [ $GC_PROJECTS_OPT -eq 1 ]; then
    gc_specified_projects
else
    gc_all_projects
fi

OUT=$(date +"%D %r Finished") && log "$OUT"

exit 0

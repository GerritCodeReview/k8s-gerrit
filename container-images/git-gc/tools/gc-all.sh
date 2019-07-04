#!/bin/ash

log() {
  # Rotate the $LOG if current date is different from the last modification of $LOG
  if test -f "$LOG" ; then
    TODAY=$(date +%Y-%m-%d)
    LOG_LAST_MODIFIED=$(date +%Y-%m-%d -r $LOG)
    if test "$TODAY" != "$LOG_LAST_MODIFIED" ; then
      mv "$LOG" "$LOG.$LOG_LAST_MODIFIED"
      gzip "$LOG.$LOG_LAST_MODIFIED"
    fi
  fi

  echo $1 | tee -a $LOG
}

TOP=/var/gerrit/git
LOG=/var/log/git/gc.log
OUT=$(date +"%D %r Started") && log "$OUT"

gc_options() {
  if test -f "$1/gc-aggressive" ; then
    echo "--aggressive"
  elif test -f "$1/gc-aggressive-once" ; then
    echo "--aggressive"
    rm -f "$1/gc-aggressive-once"
  else
    echo ""
  fi
}

log_opts() {
  if test -z $1 ; then
    echo ""
  else
    echo " [$1]"
  fi
}

find $TOP -type d -name \*.git | sed 's,^./,,' | while IFS= read -r -d $'\n' d
do
  OPTS=$(gc_options $d)
  LOG_OPTS=$(log_opts $OPTS)

  OUT=$(date +"%D %r Started: $d$LOG_OPTS") && log "$OUT"

  git --git-dir="$d" config core.logallrefupdates true

  git --git-dir="$d" config repack.usedeltabaseoffset true
  git --git-dir="$d" config repack.writebitmaps true
  git --git-dir="$d" config pack.compression 9
  git --git-dir="$d" config pack.indexversion 2

  git --git-dir="$d" config gc.autodetach false
  git --git-dir="$d" config gc.autopacklimit 4
  git --git-dir="$d" config gc.packrefs true
  git --git-dir="$d" config gc.reflogexpire never
  git --git-dir="$d" config gc.reflogexpireunreachable never
  git --git-dir="$d" config receive.autogc false

  OUT=$(git --git-dir="$d" gc --auto --prune $OPTS || date +"%D %r Failed: $d") \
    && log "$OUT"

  (find "$d/refs/changes" -type d | xargs rmdir;
   find "$d/refs/changes" -type d | xargs rmdir
  ) 2>/dev/null

  OUT=$(date +"%D %r Finished: $d$LOG_OPTS") && log "$OUT"

done

OUT=$(date +"%D %r Finished") && log "$OUT"

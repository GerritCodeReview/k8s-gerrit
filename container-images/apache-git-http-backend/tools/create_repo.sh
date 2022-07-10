#!/bin/ash
# shellcheck shell=dash

echo "Content-type: text/html"
REPO=${REQUEST_URI##/new/}

if test "$REPO" == "${REPO%.git}"; then
    REPO="${REPO}.git"
fi

STATUS_CODE="500 Internal Server Error"
MESSAGE="May bugs in the script."

if test -d "/var/gerrit/git/${REPO}"; then
    STATUS_CODE="202 Accepted"
    MESSAGE="Repository already created."
else
    git init --bare "/var/gerrit/git/${REPO}" > /dev/null
    if test -f "/var/gerrit/git/${REPO}/HEAD"; then
        STATUS_CODE="201 Created"
        MESSAGE="Repository successfully created."
    else
        MESSAGE="Repository could not be created."
    fi
fi

echo "Status: ${STATUS_CODE}"
echo ""
echo "${MESSAGE}"

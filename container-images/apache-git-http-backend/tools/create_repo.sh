#!/bin/ash

echo "Content-type: text/html"
REPO=${REQUEST_URI##/new/}

if test "$REPO" == "${REPO%.git}"; then
    REPO="${REPO}.git"
fi

STATUS_CODE="500 Internal Server Error"
MESSAGE="Repository could not be created."

git init --bare /var/gerrit/git/${REPO} > /dev/null

if test -f /var/gerrit/git/${REPO}/HEAD; then
    STATUS_CODE="201 Created"
    MESSAGE="Repository successfully created."
fi

echo "Status: ${STATUS_CODE}"
echo ""
echo "${MESSAGE}"

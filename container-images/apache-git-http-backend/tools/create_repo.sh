#!/bin/bash

echo "Content-type: text/html"
echo ""

REPO=${REQUEST_URI##/new/}

if [[ "${REPO}" != *".git" ]]; then
    REPO="${REPO}.git"
fi

git init --bare /var/gerrit/git/${REPO} > /dev/null || \
    {
        echo "Status: 400 Repository could not be created."
        exit 1
    }

if test -f /var/gerrit/git/${REPO}/HEAD; then
    echo "Status: 201 Created repository ${REPO}"
    exit 0
else
    echo "Status: 400 Repository could not be created."
    exit 1
fi

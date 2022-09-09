#!/bin/ash

delete() {
    rm -rf /var/gerrit/git/${REPO}.git

    if ! test -f /var/gerrit/git/${REPO}.git; then
        STATUS_CODE="204 No Content"
        MESSAGE="Repository ${REPO} deleted."
    else
        MESSAGE="Repository ${REPO} could not be deleted."
    fi
}

new() {
    if test -d /var/gerrit/git/${REPO}.git; then
        STATUS_CODE="200 OK"
        MESSAGE="Repository already available."
    else
        git init --bare /var/gerrit/git/${REPO}.git > /dev/null
        if test -f /var/gerrit/git/${REPO}.git/HEAD; then
            STATUS_CODE="201 Created"
            MESSAGE="Repository ${REPO} created."
        else
            MESSAGE="Repository ${REPO} could not be created."
        fi
    fi
}

update_head(){
    read -n ${CONTENT_LENGTH} POST_STRING
    NEW_HEAD=$(echo ${POST_STRING} | jq .ref - | tr -d '"')

    git --git-dir /var/gerrit/git/${REPO}.git symbolic-ref HEAD ${NEW_HEAD}

    if test "ref: ${NEW_HEAD}" == "$(cat /var/gerrit/git/${REPO}.git/HEAD)"; then
        STATUS_CODE="200 OK"
        MESSAGE="Repository HEAD updated to ${NEW_HEAD}."
    else
        MESSAGE="Repository HEAD could not be updated to ${NEW_HEAD}."
    fi
}

echo "Content-type: text/html"
REPO=${REQUEST_URI##/a/projects/}
REPO="${REPO%%.git}"

if test "${REQUEST_METHOD}" == "PUT"; then
    if [[ ${REQUEST_URI} == */HEAD ]]; then
        REPO=${REPO%"/HEAD"}
        update_head
    else
        new
    fi
elif test "${REQUEST_METHOD}" == "DELETE"; then
    delete
else
    STATUS_CODE="400 Bad Request"
    MESSAGE="Unknown method."
fi

test -z ${STATUS_CODE} && STATUS_CODE="500 Internal Server Error"

echo "Status: ${STATUS_CODE}"
echo ""
echo "${MESSAGE}"

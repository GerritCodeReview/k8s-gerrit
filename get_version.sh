GERRIT_VERSION=$(docker run --entrypoint "/bin/sh" gerrit-base \
    -c "java -jar /var/gerrit/bin/gerrit.war version")
GERRIT_VERSION=$(echo "${GERRIT_VERSION##*$'\n'}" | cut -d' ' -f3 | tr -d '[:space:]')
export GIT_REV_TAG="$(git describe --dirty)-$GERRIT_VERSION"

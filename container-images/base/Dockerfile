FROM alpine:3.10.0

RUN apk update && \
    apk add --no-cache git

ARG GERRIT_UID=1000
RUN adduser -D gerrit -u $GERRIT_UID -G users

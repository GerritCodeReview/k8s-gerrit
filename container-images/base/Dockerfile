ARG BASE_IMAGE=alpine:3.21.3
FROM $BASE_IMAGE

ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

RUN apk update && \
    apk add --no-cache git gcompat

ENV LD_PRELOAD=/lib/libgcompat.so.0

ARG GERRIT_UID=1000
RUN adduser -D gerrit -u $GERRIT_UID -G users

#!/bin/bash

SCRIPTPATH=`dirname $(readlink -f $0)`

if test -n "$(grep '#TODO' $SCRIPTPATH/**/*.yaml)"; then
    echo "Incomplete configuration. Replace '#TODO' comments with valid configuration."
    exit 1
fi

kubectl apply -f nfs/resources
helm upgrade nfs-subdir-external-provisioner \
    nfs-subdir-external-provisioner/nfs-subdir-external-provisioner \
    --values nfs/nfs-provisioner.values.yaml \
    --namespace nfs \
    --install

kubectl apply -f ldap
kubectl apply -f ingress
istioctl install -f "$SCRIPTPATH/../../istio/gerrit.profile.yaml"

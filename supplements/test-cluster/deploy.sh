#!/bin/bash

# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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

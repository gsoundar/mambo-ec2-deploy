#!/usr/bin/env bash

BASE="/opt/mambo/mambo-ec2-deploy"
HOSTS="10.0.0.10 10.0.0.11 10.0.0.12"

for HOST in ${HOSTS}
do
    echo "${HOST} Copying driver to different applications"
    scp ~/hadoop-connector-nfsv3-1.0.jar ${HOST}:${BASE}/hadoop/share/hadoop/common/lib/
    scp ~/hadoop-connector-nfsv3-1.0.jar ${HOST}:${BASE}/hbase/lib/
    scp ~/hadoop-connector-nfsv3-1.0.jar ${HOST}:${BASE}/tachyon/lib/
    scp ~/hadoop-connector-nfsv3-1.0.jar ${HOST}:${BASE}/spark/lib/
done



#!/usr/bin/env bash

HADOOP_NFS_CONN=hadoop-nfsv3-connector

cd /opt/mambo/mambo-ec2-deploy/hadoop-nfsv3-connector
mvn clean package

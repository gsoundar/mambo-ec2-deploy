#!/usr/bin/env bash

HADOOP_PACKAGE=hadoop-2.4.1
HBASE_PACKAGE=hbase-0.98.7-hadoop2

# Setup the Hadoop packages
cp -rf packages/${HADOOP_PACKAGE} hadoop
cp -rf packages/${HBASE_PACKAGE} hbase

# Copy the configuration files
mkdir /opt/mambo/mambo-ec2-deploy/hadoop/conf
cp configuration/3node/hadoop/conf/* /opt/mambo/mambo-ec2-deploy/hadoop/conf

# Setup keys
cp configuration/3node/keys/mambo_id_rsa ~/.ssh/
chmod 400 ~/.ssh/mambo_id_rsa
chown ec2-user:ec2-user ~/.ssh/mambo_id_rsa
cat configuration/3node/keys/mambo_id_rsa.pub >> ~/.ssh/authorized_keys
cat configuration/3node/keys/ssh_conf >> ~/.ssh/config
chown ec2-user:ec2-user ~/.ssh/config


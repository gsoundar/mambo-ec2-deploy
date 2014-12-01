#!/usr/bin/env bash

HADOOP_PACKAGE=hadoop-2.4.1
HBASE_PACKAGE=hbase-0.98.7-hadoop2
HADOOP_NFS_CONN=hadoop-nfsv3-connector
LOCAL_DIR=/mnt/local
CONF_NAME="3node"

# Setup the Hadoop packages
cp -rf packages/${HADOOP_PACKAGE} hadoop
cp -rf packages/${HBASE_PACKAGE} hbase
cp -rf packages/${HADOOP_NFS_CONN}/hadoop-connector-nfsv3-1.0.jar hadoop/share/hadoop/common/lib/
cp -rf packages/${HADOOP_NFS_CONN}/hadoop-connector-nfsv3-1.0.jar hbase/lib/
cp -rf hadoop/share/hadoop/common/hadoop-nfs-2.4.1.jar hbase/lib/

# Configure the OS
# Increase the file and process limits
LIMITS_CONF=/etc/security/limits.conf
echo "* hard nofile 65535" >> ${LIMITS_CONF}
echo "* soft nofile 65535" >> ${LIMITS_CONF}
echo "* hard nproc 65535" >> ${LIMITS_CONF}
echo "* soft nproc 65535" >> ${LIMITS_CONF}

# TODO: Install Oracle Java
# Seems like HBase needs this.

# Copy the configuration files
mkdir /opt/mambo/mambo-ec2-deploy/hadoop/conf
cp configuration/${CONF_NAME}/hadoop/conf/* /opt/mambo/mambo-ec2-deploy/hadoop/conf
mkdir /opt/mambo/mambo-ec2-deploy/hbase/conf
cp configuration/${CONF_NAME}/hbase/conf/* /opt/mambo/mambo-ec2-deploy/hbase/conf

# Setup keys
cp configuration/${CONF_NAME}/keys/id_rsa_mambo /home/ec2-user/.ssh/
chmod 400 /home/ec2-user/.ssh/id_rsa_mambo
chown ec2-user:ec2-user /home/ec2-user/.ssh/id_rsa_mambo
cat configuration/${CONF_NAME}/keys/id_rsa_mambo.pub >> /home/ec2-user/.ssh/authorized_keys
cat configuration/${CONF_NAME}/keys/ssh_conf >> /home/ec2-user/.ssh/config
chown ec2-user:ec2-user /home/ec2-user/.ssh/config
chmod 400 /home/ec2-user/.ssh/config

# set directories and change permissions
mkdir ${LOCAL_DIR}/namenode
mkdir ${LOCAL_DIR}/datanode
mkdir ${LOCAL_DIR}/hadoop-temp
mkdir /mnt/mamboroot
mkdir ${LOCAL_DIR}/zookeeper

chown ec2-user:ec2-user ${LOCAL_DIR}/namenode
chown ec2-user:ec2-user ${LOCAL_DIR}/datanode
chown ec2-user:ec2-user ${LOCAL_DIR}/hadoop-temp
chown ec2-user:ec2-user ${LOCAL_DIR}/zookeeper
chown -R ec2-user:ec2-user /opt/mambo/mambo-ec2-deploy/hadoop/
chown -R ec2-user:ec2-user /opt/mambo/mambo-ec2-deploy/hbase/

mount -t nfs 10.0.0.61:/mnt/data /mnt/mamboroot

#env
echo "export JAVA_HOME=/usr/lib/jvm/java-1.7.0/" >> /etc/profile


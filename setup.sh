#!/usr/bin/env bash

HADOOP_PACKAGE=hadoop-2.6.0
HADOOP_VERSION=2.6.0
HBASE_PACKAGE=hbase-0.98.7-hadoop2
SPARK_PACKAGE=spark-1.1.1-bin-hadoop2.4
HADOOP_NFS_CONN=hadoop-nfsv3-connector
TACHYON_PACKAGE=tachyon-0.5.0
LOCAL_DIR=/mnt/local
CONF_NAME="10node"

# Download packages
# Spark is too big to store on Github directly
curl http://d3kbcqa49mib13.cloudfront.net/spark-1.1.1-bin-hadoop2.4.tgz > /opt/mambo/mambo-ec2-deploy/packages/${SPARK_PACKAGE}.tgz
tar -C /opt/mambo/mambo-ec2-deploy/packages/ -xzf /opt/mambo/mambo-ec2-deploy/packages/${SPARK_PACKAGE}.tgz 

# Setup the Hadoop packages
cp -rf packages/${HADOOP_PACKAGE} hadoop
cp -rf packages/${HBASE_PACKAGE} hbase
cp -rf packages/${TACHYON_PACKAGE} tachyon
cp -rf packages/${SPARK_PACKAGE} spark

cp -rf packages/${HADOOP_NFS_CONN}/hadoop-connector-nfsv3-1.0.jar hadoop/share/hadoop/common/lib/
cp -rf packages/${HADOOP_NFS_CONN}/hadoop-connector-nfsv3-1.0.jar hbase/lib/
mkdir tachyon/lib
cp -rf packages/${HADOOP_NFS_CONN}/hadoop-connector-nfsv3-1.0.jar tachyon/lib/
cp -rf packages/${HADOOP_NFS_CONN}/hadoop-connector-nfsv3-1.0.jar spark/lib/
cp -rf packages/${TACHYON_PACKAGE}/client/target/tachyon-client-0.5.0-jar-with-dependencies.jar hadoop/share/hadoop/common/lib/
cp -rf hadoop/share/hadoop/common/hadoop-nfs-${HADOOP_VERSION}.jar hbase/lib/
cp -rf hadoop/share/hadoop/common/hadoop-nfs-${HADOOP_VERSION}.jar tachyon/lib/
cp -rf hadoop/share/hadoop/common/hadoop-nfs-${HADOOP_VERSION}.jar spark/lib/
cp -rf hadoop/share/hadoop/common/lib/netty-3.6.2.Final.jar tachyon/lib/

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
cp configuration/${CONF_NAME}/tachyon/conf/* /opt/mambo/mambo-ec2-deploy/tachyon/conf
cp configuration/${CONF_NAME}/spark/conf/* /opt/mambo/mambo-ec2-deploy/spark/conf

# Setup keys
cp configuration/${CONF_NAME}/keys/id_rsa_mambo /home/ec2-user/.ssh/
chmod 400 /home/ec2-user/.ssh/id_rsa_mambo
chown ec2-user:ec2-user /home/ec2-user/.ssh/id_rsa_mambo
cat configuration/${CONF_NAME}/keys/id_rsa_mambo.pub >> /home/ec2-user/.ssh/authorized_keys
cat configuration/${CONF_NAME}/keys/ssh_conf >> /home/ec2-user/.ssh/config
chown ec2-user:ec2-user /home/ec2-user/.ssh/config
chmod 400 /home/ec2-user/.ssh/config

# set directories and change permissions
mkdir /mnt/mamboroot
mkdir ${LOCAL_DIR}/namenode
mkdir ${LOCAL_DIR}/datanode
mkdir ${LOCAL_DIR}/hadoop-temp
mkdir ${LOCAL_DIR}/zookeeper
mkdir ${LOCAL_DIR}/spark
mkdir ${LOCAL_DIR}/spark-worker

chown ec2-user:ec2-user ${LOCAL_DIR}/namenode
chown ec2-user:ec2-user ${LOCAL_DIR}/datanode
chown ec2-user:ec2-user ${LOCAL_DIR}/hadoop-temp
chown ec2-user:ec2-user ${LOCAL_DIR}/zookeeper
chown ec2-user:ec2-user ${LOCAL_DIR}/spark
chown ec2-user:ec2-user ${LOCAL_DIR}/spark-worker
chown -R ec2-user:ec2-user /opt/mambo/mambo-ec2-deploy/hadoop/
chown -R ec2-user:ec2-user /opt/mambo/mambo-ec2-deploy/hbase/
chown -R ec2-user:ec2-user /opt/mambo/mambo-ec2-deploy/tachyon/
chown -R ec2-user:ec2-user /opt/mambo/mambo-ec2-deploy/spark/

# start the file systems
mount -t nfs 10.0.0.61:/mnt/data /mnt/mamboroot
/opt/mambo/mambo-ec2-deploy/hadoop/bin/hdfs --config  /opt/mambo/mambo-ec2-deploy/hadoop/conf/ namenode -format mambo

#env
echo "export JAVA_HOME=/usr/lib/jvm/java-1.7.0/" >> /etc/profile


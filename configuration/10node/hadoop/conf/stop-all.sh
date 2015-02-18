#!/bin/bash

HADOOP_PREFIX=/opt/mambo/mambo-ec2-deploy/hadoop/
HADOOP_YARN_HOME=${HADOOP_PREFIX}
HADOOP_CONF_DIR=${HADOOP_PREFIX}/conf/

# Yarn
ssh -t 10.0.0.10 "bash -i -c \"$HADOOP_PREFIX/sbin/hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs stop namenode\""
for x in {0..2}; do ssh -t -t 10.0.0.1${x} "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR stop nodemanager\""; done
for x in {0..2}; do ssh -t -t 10.0.0.1${x} "bash -i -c \"$HADOOP_YARN_HOME/sbin/hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs stop datanode\""; done
ssh -t 10.0.0.10 "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR stop resourcemanager\""
ssh -t 10.0.0.10 "bash -i -c \"$HADOOP_PREFIX/sbin/mr-jobhistory-daemon.sh stop historyserver\""

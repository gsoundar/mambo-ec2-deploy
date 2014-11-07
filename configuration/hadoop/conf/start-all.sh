#!/bin/bash

HADOOP_PREFIX=/x/eng/rtpbld00/scratch/jingxin/hadoop-2.4.1/
HADOOP_YARN_HOME=${HADOOP_PREFIX}
HADOOP_CONF_DIR=${HADOOP_PREFIX}/conf/

# Yarn
ssh -t scspr0016462001.gdl.englab.netapp.com "bash -i -c \"$HADOOP_PREFIX/sbin/hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs start namenode\""
ssh -t scspr0016462001.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR start resourcemanager\""
for x in {1..3}; do ssh -t -t scspr001646200${x}.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR start nodemanager\""; done
for x in {1..3}; do ssh -t -t scspr001646200${x}.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/hadoop-daemon.sh --config $HADOOP_CONF_DIR --script hdfs start datanode\""; done
ssh -t scspr0016462001.gdl.englab.netapp.com "bash -i -c \"$HADOOP_PREFIX/sbin/mr-jobhistory-daemon.sh start historyserver\""

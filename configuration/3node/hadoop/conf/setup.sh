#!/bin/bash

HADOOP_PREFIX=/opt/mambo/mambo-ec2-deploy/hadoop
HADOOP_YARN_HOME=${HADOOP_PREFIX}
HADOOP_CONF_DIR=${HADOOP_PREFIX}/conf/

# Setup directories
for x in {10..12}; do ssh -t -t 10.0.0.${x} 'sudo mkdir /mnt/namenode'; done
for x in {10..12}; do ssh -t -t 10.0.0.${x} 'sudo mkdir /mnt/datanode'; done
for x in {10..12}; do ssh -t -t 10.0.0.${x} 'sudo mkdir /mnt/hadoop-temp'; done

# Change permissions
for x in {10..12}; do ssh -t -t 10.0.0.${x} 'sudo chown ec2-user /mnt/namenode'; done
for x in {10..12}; do ssh -t -t 10.0.0.${x} 'sudo chown ec2-user /mnt/datanode'; done
for x in {10..12}; do ssh -t -t 10.0.0.${x} 'sudo chown ec2-user /mnt/hadoop-temp'; done

# Namenode
#ssh -t scspr0016462001.gdl.englab.netapp.com "bash -i -c \"${HADOOP_PREFIX}/sbin/hadoop-daemon.sh --config ${HADOOP_CONF_DIR} --script hdfs start namenode\""

# Datanodes
#for x in {1..3}; do ssh -t -t scspr001646200${x}.gdl.englab.netapp.com "bash -i -c \"${HADOOP_PREFIX}/sbin/hadoop-daemon.sh --config ${HADOOP_CONF_DIR} --script hdfs start datanode\""; done
#for x in {10..20}; do ssh -t -t scspr00161340${x}.gdl.englab.netapp.com "bash -i -c \"${HADOOP_PREFIX}/sbin/hadoop-daemon.sh --config ${HADOOP_CONF_DIR} --script hdfs start datanode\""; done

# Yarn
#for x in {1..3}; do ssh -t -t scspr001646200${x}.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR stop nodemanager\""; done
#for x in {10..20}; do ssh -t -t scspr00161340${x}.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR stop nodemanager\""; done
#ssh -t scspr0016134001.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR stop resourcemanager\""

#ssh -t scspr0016462001.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR start resourcemanager\""
#for x in {1..3}; do ssh -t -t scspr001646200${x}.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR start nodemanager\""; done
#for x in {10..20}; do ssh -t -t scspr00161340${x}.gdl.englab.netapp.com "bash -i -c \"$HADOOP_YARN_HOME/sbin/yarn-daemon.sh --config $HADOOP_CONF_DIR start nodemanager\""; done

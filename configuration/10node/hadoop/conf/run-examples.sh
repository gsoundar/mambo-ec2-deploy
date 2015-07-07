#!/bin/bash

HADOOP_PREFIX=/opt/mambo/mambo-ec2-deploy/hadoop/
NFS_HOME=/mnt/mamboroot/

#use the LICENSE.TXT as wordcount input file
sudo cp $HADOOP_PREFIX/LICENSE.txt $NFS_HOME/wc_input
$HADOOP_PREFIX/bin/hadoop jar share/hadoop/mapreduce/hadoop-mapreduce-examples-2.4.1.jar wordcount /wc_input /wc_output 

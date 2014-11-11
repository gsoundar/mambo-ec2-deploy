#!/usr/bin/env python
import os
logdir="/mnt/loon/xing/logfile"
recordnums = {'10g' : 100000000, '25g': 250000000, '50g': 500000000, '100g': 1000000000}
for size in recordnums.keys():
    recordnum = recordnums.get(size)
    print "start to run terasort at %(s)s with recordnum at %(n)d" % {'s': size, 'n': recordnum}
    teragenlog = '%(l)s/teragen-%(s)s.log' % {'l': logdir, 's': size}
    terasortlog = '%(l)s/terasort-%(s)s.log' % {'l': logdir, 's': size}
    teragencmd = "hadoop jar /mnt/loon/xing/hadoop-mapreduce-examples-2.4.0.jar teragen -D mapreduce.job.maps=25 %(r)d /tera2/in%(s)s 1>%(l)s 2>&1" % {'r': recordnum, 's': size, 'l': teragenlog}
    terasortcmd = "hadoop jar /usr/local/hadoop-mapreduce-examples-2.4.0.jar terasort -D mapreduce.job.reduces=12 /tera2/in%(s)s /tera2/out%(s)s 1>%(l)s 2>&1" % {'s': size, 'l': terasortlog}
    print teragencmd
    print terasortcmd
    os.system(teragencmd)
    os.system(terasortcmd)

#!/usr/bin/env python
# 
# note that we probably need to clear NFS server cache for dfsread benchmarks.
#
import os
logdir="/mnt/loon/xing/logfile"
Filenums = [1, 2, 4, 8, 16, 32]
for filenum in Filenums:
    print "start to run DFSIO with filenum at %(n)d" % {'n': filenum}
    dfswritelog = '%(l)s/DFS-write-filenum.%(n)d.size.10g.log' % {'l': logdir, 'n': filenum}
    dfswritecmd = "hadoop org.apache.hadoop.fs.TestDFSIO -write -nrFiles %(n)d -fileSize 10GB 1>%(l)s 2>&1" % {'n': filenum, 'l': dfswritelog}
    print dfswritecmd
#    os.system(dfswritecmd)

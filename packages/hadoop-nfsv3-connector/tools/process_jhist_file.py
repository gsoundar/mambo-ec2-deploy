#!/usr/bin/env python

import os
import sys
import json
import numpy

# Process command-line arguments
if(len(sys.argv) != 2):
    sys.stderr.write('USAGE: %(p)s <jhist-file>\n' % {'p':sys.argv[0]})
    sys.exit(-1)

filename = sys.argv[1]

# Modules

# Host distribution
def compute_host_distribution(doc, state):
    if(state.has_key('cookie') == False):
        state['cookie'] = dict()
    cookie = state['cookie']
    if(doc['type'].find('MAP_ATTEMPT_STARTED') != -1):
        event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.TaskAttemptStarted']
        id = event['taskid']
        hostname = event['trackerName']
        if(cookie.has_key(hostname) == False):
            cookie[hostname] = []
        cookie[hostname].append(id)
    return state

# Task distribution
def compute_task_distribution(doc, state):
    if(state.has_key('cookie') == False):
        state['cookie'] = dict()
    cookie = state['cookie']
    if(doc['type'].find('TASK_FINISHED') != -1):
        event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.TaskFinished']
        id = event['taskid']
        tasktype = event['taskType']
        status = event['status']
        if(cookie.has_key(tasktype) == False):
            cookie[tasktype] = []
        cookie[tasktype].append(id)
    return state

# Task times
def compute_task_times(doc, state):
    if(doc['type'].find('MAP_ATTEMPT_STARTED') != -1 or doc['type'].find('REDUCE_ATTEMPT_STARTED') != -1):
        event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.TaskAttemptStarted']
        id = event['taskid']
        start_time = event['startTime']
        hostname = event['trackerName']
        cookie = {'taskid':id, 'start_time':start_time, 'hostname': hostname}
        if(state.has_key('cookie') == False):
            state['cookie'] = dict()
        state['cookie'][id] = cookie
        #print 'Map %(id)s started at %(st)d on host %(h)s' % {'id':id, 'st':start_time, 'h':hostname }
    elif(doc['type'].find('MAP_ATTEMPT_FAILED') != -1):
        event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.TaskAttemptUnsuccessfulCompletion']
        id = event['taskid']
        finish_time = event['finishTime']
        status = event['status']
        hostname = event['hostname']
        cookie = state['cookie'][id]
        cookie['finish_time'] = finish_time
        cookie['status'] = status
        #print 'Map %(id)s finished with status %(stat)s at %(ft)d on host %(h)s' % {'id':id, 'stat':status, 'ft':finish_time, 'h':hostname }
    elif(doc['type'].find('MAP_ATTEMPT_FINISHED') != -1):
        event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.MapAttemptFinished']
        id = event['taskid']
        finish_time = event['finishTime']
        status = event['taskStatus']
        hostname = event['hostname']
        counts = event['counters']['groups'][0]['counts']
        cookie = state['cookie'][id]
        cookie['finish_time'] = finish_time
        cookie['status'] = status
        cookie['counts'] = counts
        start_time = cookie['start_time']
        #print 'Map %(id)s finished with status %(stat)s at %(ft)d on host %(h)s' % {'id':id, 'stat':status, 'ft':finish_time, 'h':hostname }
        #print 'Map %(id)s finished with status %(stat)s took %(lat)10.3f seconds on host %(h)s' % {'id':id, 'stat':status, 'lat':(finish_time-start_time)/1000.0, 'h':hostname }
    elif(doc['type'].find('REDUCE_ATTEMPT_FINISHED') != -1):
        event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.ReduceAttemptFinished']
        id = event['taskid']
        finish_time = event['finishTime']
        status = event['taskStatus']
        hostname = event['hostname']
        counts = event['counters']['groups'][0]['counts']
        cookie = state['cookie'][id]
        cookie['finish_time'] = finish_time
        cookie['status'] = status
        cookie['counts'] = counts
        start_time = cookie['start_time']
    return state
    

def compute_running_tasks(doc, state, tasks):
    if(doc['type'].find('MAP_ATTEMPT_STARTED') != -1 or doc['type'].find('REDUCE_ATTEMPT_STARTED') != -1):
       event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.TaskAttemptStarted']
       currenttime = event['startTime']
       tasks[0] += 1
       state.append([currenttime, tasks[0] - tasks[1]])
    elif(doc['type'].find('MAP_ATTEMPT_FINISHED') != -1):
       event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.MapAttemptFinished']
       currenttime = event['finishTime']
       tasks[1] += 1
       state.append([currenttime, tasks[0] - tasks[1]]);
    elif(doc['type'].find('MAP_ATTEMPT_FAILED') != -1 or \
       doc['type'].find('MAP_ATTEMPT_KILLED') != -1 or \
       doc['type'].find('REDUCE_ATTEMPT_KILLED') != -1 or \
       doc['type'].find('REDUCE_ATTEMPT_FAILED') != -1):
       event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.TaskAttemptUnsuccessfulCompletion']
       currenttime = event['finishTime']
       tasks[1] += 1
       state.append([currenttime, tasks[0] - tasks[1]]);
    elif(doc['type'].find('REDUCE_ATTEMPT_FINISHED') != -1):
       event = doc['event']['org.apache.hadoop.mapreduce.jobhistory.ReduceAttemptFinished']
       currenttime = event['finishTime']
       tasks[1] += 1
       state.append([currenttime, tasks[0] - tasks[1]]);
    return state

# Run through the file
def process(fd, mods):
    tasks = [0, 0]
    for line in fd:
        try:
            doc = json.loads(line)
            # Call different modules from here
            mods['compute_task_times'] = compute_task_times(doc, mods['compute_task_times'])
            mods['compute_host_distribution'] = compute_host_distribution(doc, mods['compute_host_distribution'])
            mods['compute_task_distribution'] = compute_task_distribution(doc, mods['compute_task_distribution'])
            mods['compute_running_tasks'] = compute_running_tasks(doc, mods['compute_running_tasks'], tasks)
        except ValueError:
            #sys.stderr.write('Got a parsing error with line %(l)s\n\n' % {'l':line})
            continue
            

# Main
def main(filename):
    try:
        fd = open(filename, 'r')
        mods = dict()
        mods['compute_task_times'] = dict()
        mods['compute_task_distribution'] = dict()
        mods['compute_host_distribution'] = dict()
        mods['compute_running_tasks'] = []
        process(fd, mods)
        fd.close()
        return mods
    except IOError:
        sys.stderr.write('Could not open jhist file %(f)s' % {'f':filename})
        return None

# Main
data = main(filename)

# Print
# Task times
print 'Task stats'
lats=[]
d = data['compute_task_times']['cookie']
for h in d.keys():
    lats.append(d[h]['finish_time'] - d[h]['start_time'])
n = len(lats)
lats.sort()
print '%(h)50s %(l)10.3f %(u)5s' % {'h': 'Median task time', 'l':lats[n/2], 'u':'ms'}

# get read/write bytes and bandwidths
print 'Read/write bytes and bandwidth'
localfsreadbytes=0
localfswritebytes=0
nfsreadbytes=0
nfswritebytes=0
localfsreadtime=0
localfswritetime=0
nfsreadtime=0
nfswritetime=0
d = data['compute_task_times']['cookie']
first_task_start_time=0
last_task_finish_time=0
for h in d.keys():
    task_start_time = d[h]['start_time']
    task_finish_time = d[h]['finish_time']
    if first_task_start_time == 0:
      first_task_start_time = task_start_time
    elif task_start_time < first_task_start_time:
        first_task_start_time = task_start_time
    if task_finish_time > last_task_finish_time:
        last_task_finish_time = task_finish_time
    tasklocalfsread = d[h]['counts'][0]['value']
    if tasklocalfsread != 0:
        localfsreadbytes +=  tasklocalfsread
        localfsreadtime += task_finish_time - task_start_time
    tasklocalfswrite = d[h]['counts'][1]['value']
    if tasklocalfswrite != 0:
        localfswritebytes +=  tasklocalfswrite
        localfswritetime += task_finish_time - task_start_time
    tasknfsread = d[h]['counts'][5]['value']
    if tasknfsread != 0:
        nfsreadbytes +=  tasknfsread
        nfsreadtime += task_finish_time - task_start_time
    tasknfswrite = d[h]['counts'][6]['value']
    if tasknfswrite != 0:
        nfswritebytes +=  tasknfswrite
        nfswritetime += task_finish_time - task_start_time
print "task started at %(s)d, finished at %(f)d" % {'s':first_task_start_time, 'f':last_task_finish_time}
runtime = (last_task_finish_time - first_task_start_time)/1000.0
print "runtime %(t)10.3f s" % {'t':runtime}
print "File System Counters:\n"
print 'local file system read bytes: %(b)d' % {'b': localfsreadbytes}
print 'local file system written bytes: %(b)d' % {'b': localfswritebytes}
print 'NFS read bytes: %(b)d' % {'b': nfsreadbytes}
print 'NFS written bytes: %(b)d\n' % {'b': nfswritebytes}

print "Average by runtime: NFS FileSystem read/write bandwidths"
print '%(h)50s %(l)10.3f MB/s' % {'h': ' read Bandwidth', 'l': nfsreadbytes/1024.0/1024/runtime}
print '%(h)50s %(l)10.3f MB/s\n' % {'h': ' write Bandwidth', 'l': nfswritebytes/1024.0/1024/runtime}

print "(per-task) Local File System Read/Write Bandwidths:\n"
if localfsreadtime != 0:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average read Bandwidth', 'l': localfsreadbytes/1024.0/1024*1000/localfsreadtime}
else:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average read Bandwidth', 'l': 0}
   
if localfswritetime != 0:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average write Bandwidth', 'l':localfswritebytes/1024.0/1024*1000/localfswritetime}
else:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average write Bandwidth', 'l':0}
print "(per-task) NFS Read/Write Bandwidths:\n"
if nfsreadtime != 0:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average read Bandwidth', 'l':nfsreadbytes/1024.0/1024*1000/nfsreadtime}
else:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average read Bandwidth', 'l':0}
if nfswritetime != 0:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average write Bandwidth', 'l':nfswritebytes/1024.0/1024*1000/nfswritetime}
else:
    print '%(h)50s %(l)10.3f MB/s' % {'h': 'Average write Bandwidth', 'l':0}

# Host Distribution
print 'Host distribution'
d = data['compute_host_distribution']['cookie']
for h in d.keys():
    print '%(h)50s %(t)10d' % {'h':h, 't':len(d[h])}

# Task Distribution
print 'Task distribution'
d = data['compute_task_distribution']['cookie']
for h in d.keys():
    print '%(h)50s %(t)10d' % {'h':h, 't':len(d[h])}

# Running tasks
print 'Running tasks'
running_tasks = data['compute_running_tasks']
for cur_task in running_tasks:
    print cur_task

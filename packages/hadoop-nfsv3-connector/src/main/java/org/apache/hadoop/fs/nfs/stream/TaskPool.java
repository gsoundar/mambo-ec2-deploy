package org.apache.hadoop.fs.nfs.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TaskPool {

  final int numThreads;
  final Map<Integer,Thread> threads;
  final Map<Integer, BlockingQueue<StreamTask>> queues;
  final Map<Integer,StreamTask> ongoing;
  final AtomicInteger numRunningThreads;
  final CountDownLatch shutdownLatch;
  
  public final static Log LOG = LogFactory.getLog(TaskPool.class);
  
  public TaskPool(int numThreads) {
    this.numThreads = numThreads;
    this.threads = new ConcurrentHashMap<Integer,Thread>();
    this.queues = new ConcurrentHashMap<Integer, BlockingQueue<StreamTask>>();
    this.ongoing = new ConcurrentHashMap<Integer, StreamTask>();
    this.shutdownLatch = new CountDownLatch(numThreads);
    
    // Create the task queues
    for(int i = 0; i < numThreads; ++i) {
      BlockingQueue<StreamTask> queue = new LinkedBlockingQueue<StreamTask>(2);
      queues.put(i, queue);
    }
    
    // Create the threads
    numRunningThreads = new AtomicInteger(0);
    for(int i = 0; i < numThreads; ++i) {
      Thread t = new Thread(new TaskRunner(this, i, queues.get(i)));
      threads.put(i, t);
      numRunningThreads.incrementAndGet();
      t.start();
    }
  }
  
  public void submitAny(StreamTask task) {
    int enqueued = 0;
    assert(task != null);
    assert(numRunning() == numThreads);
    int id = task.getTaskId() % numThreads;
    BlockingQueue<StreamTask> queue = queues.get(id);
    ongoing.put(task.getTaskId(), task);
    while(true) {
      try {
        while(queue.offer(task, 10, TimeUnit.SECONDS) == false) {
          LOG.info("Could not enqueue task id=" + task.getTaskId() + " into queue id=" + id + ". Trying again.");
        }
        enqueued++;
        break;
      } catch(InterruptedException exception) {
        exception.printStackTrace();
        continue;
      }
    }
    assert(ongoing.containsKey(task.getTaskId()));
    task.go();
    assert(enqueued == 1);
  }
  
  public void submitAll(StreamTask task) {
    int enqueued = 0;
    ongoing.put(task.getTaskId(), task);
    assert(numRunning() == numThreads);
    for(int i = 0; i < numThreads; ++i) {
      BlockingQueue<StreamTask> queue = queues.get(i);
      while(true) {
        try {
          while(queue.offer(task, 10, TimeUnit.SECONDS) == false) {
            LOG.info("Could not enqueue task id=" + task.getTaskId() + " into queue id=" + i + ". Trying again.");
          }
          enqueued++;
          break;
        } catch(InterruptedException exception) {
          exception.printStackTrace();
          continue;
        }
      }
    }
    assert(ongoing.containsKey(task.getTaskId()));
    task.go();
    assert(enqueued == numThreads);
  }
  
  public void taskDone(StreamTask task) {
    assert(task.isDone());
    if(task.hasError() == false) {
      LOG.debug("Task id=" + task.getTaskId() + " op=" + task.getOperation() + " is going to be removed from ongoing");
      ongoing.remove(task.getTaskId());
    }
  }
  
  public int doneThread(int id) {
    shutdownLatch.countDown();
    return numRunningThreads.decrementAndGet();
  }
  
  public int numRunning() {
    return numRunningThreads.get();
  }
  
  public int size() {
    return numThreads;
  }
  
  public List<StreamTask> shutdown() {
    ShutdownTask task = new ShutdownTask(numThreads);
    LOG.info("ShutdownTask has id=" + task.getTaskId());
    submitAll(task);
    LOG.info("Waiting for TaskPool to shutdown");
    task.await();
    while(true) {
      try {
        shutdownLatch.await();
        break;
      } catch(InterruptedException exception) {
        //Ignore
      }
    }
    LOG.info("TaskPool has been shutdown");
    return new ArrayList<StreamTask>(ongoing.values());
  }
  
}

package org.apache.hadoop.fs.nfs.stream;

import java.util.concurrent.BlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TaskRunner implements Runnable {

  final int id;
  final TaskPool pool;
  final BlockingQueue<StreamTask> queue;
  
  public final static Log LOG = LogFactory.getLog(TaskRunner.class);
  
  public TaskRunner(TaskPool pool, Integer id, BlockingQueue<StreamTask> queue) {
    this.id = id;
    this.pool = pool;
    this.queue = queue;
  }
  
  public int getId() {
    return id;
  }
  
  @Override
  public void run() {

    try {
      while(true) {
        StreamTask task = null;
        try {
          task = queue.take();
          task.waitForGo();
          
          LOG.debug("Task id=" + task.getTaskId() + " op=" + task.getOperation() + " going to be executed by runner id=" + id);
          task.execute();
          LOG.debug("Task id=" + task.getTaskId() + " op=" + task.getOperation() + " done executed by runner id=" + id);
          if(task.isDone()) {
            LOG.debug("Task id=" + task.getTaskId() + " op=" + task.getOperation() + " marking done by runner id=" + id);
            pool.taskDone(task);
          }
          
          if(task instanceof ShutdownTask) {
            LOG.debug("Runner id=" + id + " is shutting down!");
            task = null;
            break;
          }
          task = null;
          LOG.debug("Runner id=" + id + " is going for more ...");
        } catch(InterruptedException exception) {
          exception.printStackTrace();
          assert(task == null);
        }
      }
    } finally {
      LOG.debug("Runner id=" + id + " is exiting");
      pool.doneThread(id);
    }
  }

}

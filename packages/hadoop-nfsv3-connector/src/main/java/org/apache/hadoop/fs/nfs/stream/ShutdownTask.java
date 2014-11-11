package org.apache.hadoop.fs.nfs.stream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ShutdownTask extends StreamTask {

  final CountDownLatch countdown;
  
  public final static Log LOG = LogFactory.getLog(ShutdownTask.class);
  
  public ShutdownTask(int numThreads) {
    super(StreamOperation.CLOSE);
    countdown = new CountDownLatch(numThreads);
  }

  @Override
  public void execute() {
    countdown.countDown();
  }
  
  public void await() {
    while(true) {
      try {
        while(countdown.await(10, TimeUnit.SECONDS) == false) {
          LOG.info("Shutdown id =" + this.getTaskId() + " waiting for " + countdown.getCount() + " threads to complete ...");
        }
        return;
      }catch(InterruptedException exception) {
        exception.printStackTrace();
        continue;
      }
    }
  }
  
  @Override
  public boolean isDone() {
    return (countdown.getCount() == 0L);
  }
  
  public int getCount() {
    return (int) countdown.getCount();
  }
  
}

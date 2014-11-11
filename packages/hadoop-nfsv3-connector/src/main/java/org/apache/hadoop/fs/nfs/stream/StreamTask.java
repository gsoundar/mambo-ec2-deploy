package org.apache.hadoop.fs.nfs.stream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class StreamTask {

  final Integer taskId;
  final StreamOperation operation;
  final CountDownLatch go;
  final AtomicBoolean errored;
  Exception exception;
  
  static final AtomicInteger counter;
  
  public enum StreamOperation { MOCK_SINGLE, MOCK_MULTI, READ_BLOCK, PREFETCH_BLOCK, WRITE_BLOCK, FLUSH, CLOSE };
  
  static {
    counter = new AtomicInteger(1);
  }
  
  public StreamTask(StreamOperation operation) {
    this.operation = operation;
    taskId = counter.getAndIncrement();
    go = new CountDownLatch(1);
    errored = new AtomicBoolean(false);
  }

  public int getTaskId() {
    return taskId;
  }
  
  public StreamOperation getOperation() {
    return operation;
  }
  
  public void error(Exception exception) {
    errored.set(true);
    this.exception = exception;
  }
  
  public boolean hasError() {
    return errored.get();
  }
  
  public Exception getError() {
    return exception;
  }
  
  public void go() {
    go.countDown();
  }
  
  public void waitForGo() throws InterruptedException {
    go.await();
  }
  
  public abstract void execute();
  
  public abstract boolean isDone();
  
  public abstract void await();
  
}

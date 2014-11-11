package org.apache.hadoop.fs.nfs.stream;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.nfs.NFSFileSystemStore;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3Status;
import org.apache.hadoop.nfs.nfs3.response.COMMIT3Response;

public class CommitTask extends StreamTask {

  final NFSBufferedOutputStream stream;
  final NFSFileSystemStore store;
  final FileHandle handle;
  final Long offset;
  final Integer length;
  final AtomicBoolean first;
  final CountDownLatch countdown;

  public final static Log LOG = LogFactory.getLog(WritebackTask.class);
  
  public CommitTask(NFSBufferedOutputStream stream, Integer numThreads, NFSFileSystemStore store, FileHandle handle, Long offset, Integer length) {
    super(StreamOperation.FLUSH);
    this.stream = stream;
    this.store = store;
    this.handle = handle;
    this.offset = offset;
    this.length = length;
    first = new AtomicBoolean(true);
    countdown = new CountDownLatch(numThreads);
  }

  @Override
  public void execute() {
    try {
      if(first.getAndSet(false) == true) {
        COMMIT3Response response = store.commit(handle, offset, length, store.getCredentials());
        int status = response.getStatus();
        if (status != Nfs3Status.NFS3_OK) {
          throw new IOException("Commit error: status=" + status);
        }
      }
    } catch(Exception exception) {
      LOG.error("Commit task id=" + this.getTaskId() + " handle=" + handle + " offset=" + offset + " length=" + length + " failed"); 
      exception.printStackTrace();
    } finally {
      countdown.countDown();
    }
  }
  
  public void await() {
    while(true) {
      try {
        while(countdown.await(10, TimeUnit.SECONDS) == false) {
          LOG.info("Commit task id=" + this.getTaskId() + " waiting for " + countdown.getCount() + " threads to complete ...");
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

}

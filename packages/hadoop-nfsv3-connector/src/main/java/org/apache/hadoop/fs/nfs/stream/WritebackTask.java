package org.apache.hadoop.fs.nfs.stream;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.nfs.NFSFileSystemStore;
import org.apache.hadoop.fs.nfs.StreamStatistics;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3Status;
import org.apache.hadoop.nfs.nfs3.Nfs3Constant.WriteStableHow;
import org.apache.hadoop.nfs.nfs3.response.WRITE3Response;

public class WritebackTask extends StreamTask {

  final NFSFileSystemStore store;
  final FileHandle handle;
  final StreamStatistics statistics;
  final Long blockId;
  final StreamBlock block;
  final AtomicBoolean done;
  final CountDownLatch countdown;
  
  public final static Log LOG = LogFactory.getLog(WritebackTask.class);
  
  public WritebackTask(NFSFileSystemStore store, FileHandle handle, StreamStatistics statistics, Long blockId, StreamBlock block) {
    super(StreamOperation.WRITE_BLOCK);
    this.store = store;
    this.handle = handle;
    this.statistics = statistics;
    this.blockId = blockId;
    this.block = block;
    this.done = new AtomicBoolean(false);
    this.countdown = new CountDownLatch(1);
  }

  @Override
  public void execute() {
    long startTime = System.currentTimeMillis();
    
    try {
      long writeOffset = (blockId << block.getBlockSizeBits()) + block.getDataStartOffset();
      byte buffer[] = new byte[block.getDataLength()];
      System.arraycopy(block.array(), block.getDataStartOffset(), buffer, 0, block.getDataLength());
      
      WRITE3Response response = store.write(handle, writeOffset, block.getDataLength(), WriteStableHow.DATA_SYNC, buffer, store.getCredentials());
      int status = response.getStatus();
      if (status != Nfs3Status.NFS3_OK) {
        throw new IOException("NFS write error: status=" + status);
      }
      if(response.getCount() != block.getDataLength()) {
        throw new IOException("NFS write error: status=" + status + " short write copied=" + response.getCount() + " but length=" + block.getDataLength());
      }
    } catch(Exception exception) {
      exception.printStackTrace();
      error(exception);
    } finally {
      statistics.incrementNFSWriteOps(1);
      statistics.incrementBytesNFSWritten(block.getDataLength());
      statistics.incrementTimeNFSWritten(System.currentTimeMillis() - startTime);
      done.set(true);
      countdown.countDown();
    }
  }
  
  @Override
  public boolean isDone() {
    return done.get();
  }
  
  @Override
  public void await() {
    while(true) {
      try {
        while(countdown.await(10, TimeUnit.SECONDS) == false) {
          LOG.info("Writeback task id=" + this.getTaskId() + " waiting for " + countdown.getCount() + " threads to complete ...");
        }
        return;
      }catch(InterruptedException exception) {
        exception.printStackTrace();
        continue;
      }
    }
  }

}

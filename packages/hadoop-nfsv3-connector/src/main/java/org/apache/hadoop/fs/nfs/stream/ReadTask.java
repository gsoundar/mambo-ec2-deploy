package org.apache.hadoop.fs.nfs.stream;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.nfs.NFSFileSystemStore;
import org.apache.hadoop.fs.nfs.StreamStatistics;
import org.apache.hadoop.fs.nfs.rpc.RpcException;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3Status;
import org.apache.hadoop.nfs.nfs3.response.READ3Response;

public class ReadTask extends StreamTask {

  final NFSFileSystemStore store;
  final FileHandle handle;
  final StreamStatistics statistics;
  final Long blockId;
  final StreamBlock block;
  final AtomicBoolean done;
  final CountDownLatch countdown;
  
  public final static Log LOG = LogFactory.getLog(ReadTask.class);
  
  public ReadTask(NFSFileSystemStore store, FileHandle handle, StreamStatistics statistics, Long blockId, StreamBlock block) {
    super(StreamOperation.READ_BLOCK);
    this.store = store;
    this.handle = handle;
    this.statistics = statistics;
    this.blockId = blockId;
    this.block = block;
    this.countdown = new CountDownLatch(1);
    this.done = new AtomicBoolean(false);
  }

  @Override
  public void execute() {
    try {
      long startTime = System.currentTimeMillis(), stopTime;
      long readOffset = (blockId << block.getBlockSizeBits());
      
      READ3Response read3Response =
          store.read(handle, readOffset, block.getBlockSize(), store.getCredentials());
      int status = read3Response.getStatus();
      if (status != Nfs3Status.NFS3_OK) {
        throw new RpcException("NFS_READ error: status=" + status);
      }
  
      int readBytes = read3Response.getCount();
      if (readBytes == 0) {
        if (!read3Response.isEof()) {
          throw new RpcException("read 0 bytes while not reaching EOF");
        }
      }
      
      if(readBytes > 0) {
        block.writeToBlock(read3Response.getData().array(), 0, 0, readBytes);
      }
      stopTime = System.currentTimeMillis();
      
      if(readBytes >= 0) {
        statistics.incrementBytesNFSRead(readBytes);
        statistics.incrementNFSReadOps(1);
        statistics.incrementTimeNFSRead((stopTime - startTime));
      }
    } catch(Exception exception) {
      error(exception);
    } finally {
      block.setReady(true);
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
          LOG.info("ReadTask waiting for " + countdown.getCount() + " threads to complete ...");
        }
        return;
      }catch(InterruptedException exception) {
        exception.printStackTrace();
        continue;
      }
    }
  }

}

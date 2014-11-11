package org.apache.hadoop.fs.nfs.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.nfs.NFSFileSystemStore;
import org.apache.hadoop.fs.nfs.StreamStatistics;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3FileAttributes; 

public class NFSBufferedOutputStream extends OutputStream {

  final FileHandle handle;
  final Path path;
  final String pathString;
  final StreamStatistics statistics;
  final NFSFileSystemStore store;
  final AtomicBoolean closed;
  final int blockSizeBits;
  final int poolSize;
  final TaskPool pool;
  
  long fileOffset;
  StreamBlock currentBlock;
  
  private static final int MIN_WRITEBACK_POOL_SIZE = 1;
  private static final int MAX_WRITEBACK_POOL_SIZE = 16;
  private static final int DEFAULT_WRITEBACK_POOL_SIZE = 8;
  
  static final AtomicInteger streamId;
  
  public final static Log LOG = LogFactory.getLog(NFSBufferedOutputStream.class);
  
  static {
   streamId = new AtomicInteger(1);
  }
  
  public NFSBufferedOutputStream(Configuration configuration, FileHandle handle, Path path, NFSFileSystemStore store, int blockSizeBits, boolean append) throws IOException {

    this.handle = handle;
    this.path = path;
    this.pathString = path.toUri().getPath();
    
    poolSize =
        Math.min(MAX_WRITEBACK_POOL_SIZE, Math.max(MIN_WRITEBACK_POOL_SIZE, configuration
            .getInt("fs.nfs.numwritebackthreads", DEFAULT_WRITEBACK_POOL_SIZE)));
    
    this.statistics = new StreamStatistics(NFSBufferedInputStream.class + pathString, streamId.getAndIncrement(), false);
    this.store = store;
    this.blockSizeBits = blockSizeBits;
    this.currentBlock = null;
    this.closed = new AtomicBoolean(false);

    assert(blockSizeBits >= 0 && blockSizeBits <= 22);
    
    // Create the task queues
    pool = new TaskPool(poolSize);

    // Set file offset to 0 or file length
    if (append) {
      Nfs3FileAttributes attributes = store.getFileAttributes(handle, store.getCredentials());
      if(attributes != null) {
        fileOffset = attributes.getSize();
        LOG.info("Appending to file so starting at offset = " + fileOffset);
      } else {
        throw new IOException("Could not get file length");
      }
    } else {
      fileOffset = 0L;
    }

  }
  
  @Override
  public void write(int b) throws IOException {
    byte buffer[] = new byte[1];
    buffer[0] = (byte) b;
    _write(buffer, 0, 1);
  }

  @Override
  public void write(byte[] data) throws IOException {
    _write(data, 0, data.length);
  }
  
  @Override
  public void write(byte[] data, int offset, int length) throws IOException {
    long startTime = System.currentTimeMillis();
    try {
      _write(data, offset, length);
    } finally {
      statistics.incrementBytesWritten(length);
      statistics.incrementWriteOps(1);
      statistics.incrementTimeWritten((System.currentTimeMillis() - startTime));
    }
  }

  private synchronized void _write(byte[] data, int offset, int length) throws IOException {

    int lengthToWrite = Math.min(data.length, length);
    int blockSize = (int) (1 << blockSizeBits);
    long loBlockId = (long) (fileOffset >> blockSizeBits);
    long hiBlockId = (long) ((fileOffset + lengthToWrite - 1) >> blockSizeBits);
    int loOffset = (int) (fileOffset - (loBlockId << blockSizeBits));
    int hiOffset = (int) ((fileOffset + lengthToWrite - 1) - (hiBlockId << blockSizeBits));

    if(closed.get() == true) {
      throw new IOException("Stream is already closed");
    }

    // All the data is in one block, so it's easy to handle
    if (loBlockId == hiBlockId) {
      StreamBlock block = getBlock(loBlockId);
      assert(block != null);

      int bytesWritten = block.writeToBlock(data, offset, loOffset, lengthToWrite);
      assert(bytesWritten == lengthToWrite);
      fileOffset += bytesWritten;
    }
    // The data is in multiple blocks, so we need do much more work
    else {
      int totalBytesWritten = offset;
      for (long blk = loBlockId; blk <= hiBlockId; blk++) {
        StreamBlock block = getBlock(blk);
        assert(block != null);

        // We write from loOffset in the starting block
        if (blk == loBlockId) {
          int bytesWritten = block.writeToBlock(data, offset, loOffset, blockSize - loOffset);
          assert(bytesWritten == (blockSize - loOffset));
          totalBytesWritten += bytesWritten;
          fileOffset += bytesWritten;
        } 
        // We write up to hiOffset in the ending block
        else if (blk == hiBlockId) {
          int bytesWritten = block.writeToBlock(data, totalBytesWritten, 0, hiOffset + 1);
          assert(bytesWritten == (hiOffset +1));
          totalBytesWritten += bytesWritten;
          fileOffset += bytesWritten;
        } 
        // Middle blocks are written fully
        else {
          int bytesWritten = block.writeToBlock(data, totalBytesWritten, 0, blockSize);
          assert(bytesWritten == (hiOffset +1));
          totalBytesWritten += bytesWritten;
          fileOffset += bytesWritten;
        }
      }
    }
  }

  @Override
  public synchronized void flush() throws IOException {
    try {
      if(closed.get() == true) {
        throw new IOException("Stream is already closed");
      }
      if(currentBlock != null) {
        flushBlock(currentBlock);
      }
      
      CommitTask task = new CommitTask(this, pool.size(), store, handle, 0L, 0);
      pool.submitAll(task);
      LOG.debug("Submitted commit task id=" + task.getTaskId());
      task.await();
      return;
    } catch(Exception exception) {
      throw new IOException("Could not flush stream");
    }
  }
  
  @Override
  public synchronized void close() throws IOException {
    flush();
    if(closed.get() == true) {
      throw new IOException("Stream is already closed");
    }
    closed.set(true);
    
    List<StreamTask> failed = pool.shutdown();  
    if(failed != null && !failed.isEmpty()) {
      for(StreamTask task : failed) {
        LOG.error("Leftover task id=" + task.getTaskId() + " type="
            + task.getOperation() + " done=" + task.isDone() + " error="
            + task.hasError());
      }
      throw new IOException("Could not complete all tasks. File may be corrupt!");
    }
    LOG.info(statistics);
  }
  
  private StreamBlock getBlock(long blockId) throws IOException {
    if(currentBlock != null && blockId == currentBlock.getBlockId()) {
      return currentBlock;
    }
    else {
      if(currentBlock != null) {
        flushBlock(currentBlock);
      }
      currentBlock = new StreamBlock(blockSizeBits);
      currentBlock.setBlockId(blockId);
      return currentBlock;
    }
  }
  
  private void flushBlock(StreamBlock block) throws IOException {
    WritebackTask task = new WritebackTask(store, handle, statistics, block.getBlockId(), currentBlock);
    pool.submitAny(task);
    LOG.debug("Submitted writeback task id=" + task.getTaskId() + " for block id=" + block.getBlockId());
  }
  
}

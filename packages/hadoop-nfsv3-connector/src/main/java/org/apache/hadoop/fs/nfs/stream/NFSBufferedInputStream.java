package org.apache.hadoop.fs.nfs.stream;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.nfs.NFSFileSystemStore;
import org.apache.hadoop.fs.nfs.StreamStatistics;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3FileAttributes;

public class NFSBufferedInputStream extends FSInputStream {

  long fileLength;
  long fileOffset;
  long prefetchBlockLimit;
  StreamBlock currentBlock;

  final NFSFileSystemStore store;
  final FileHandle handle;

  final String pathString;
  final int readBlockSizeBits;
  final long splitSize;
  final StreamStatistics statistics;
  final TaskPool pool;
  final int poolSize;
  final boolean doPrefetch;
  final AtomicBoolean closed;
  final Map<Long, StreamTask> ongoing;
  final Map<Long, StreamBlock> cache;

  static final AtomicInteger streamId;

  public static final int DEFAULT_CACHE_SIZE_IN_BLOCKS = 64;

  public static final int MIN_PREFETCH_POOL_SIZE = 1;
  public static final int MAX_PREFETCH_POOL_SIZE = 16;
  public static final int DEFAULT_PREFETCH_POOL_SIZE = 8;
  public static final boolean DEFAULT_PREFETCH_ENABLED = true;

  public static final int DEFAULT_READAHEAD_SIZE = 16;

  public final static Log LOG = LogFactory.getLog(NFSBufferedInputStream.class);

  static {
    streamId = new AtomicInteger(1);
  }

  public NFSBufferedInputStream(Configuration configuration,
      NFSFileSystemStore store, FileHandle handle, Path f, Configuration conf,
      int readBlockSizeBits, String scheme, long splitSize,
      FileSystem.Statistics fsStat) throws IOException {

    this.store = store;
    this.handle = handle;
    this.pathString = f.toUri().getPath();

    poolSize =
        Math.min(MAX_PREFETCH_POOL_SIZE, Math.max(MIN_PREFETCH_POOL_SIZE, conf
            .getInt("fs.nfs.numprefetchthreads", DEFAULT_PREFETCH_POOL_SIZE)));
    doPrefetch = conf.getBoolean("fs.nfs.prefetch", DEFAULT_PREFETCH_ENABLED);

    this.fileOffset = 0L;
    this.readBlockSizeBits = readBlockSizeBits;
    this.splitSize = splitSize;
    this.closed = new AtomicBoolean(false);
    this.ongoing = new ConcurrentHashMap<Long, StreamTask>(poolSize);
    this.cache =
        new ConcurrentHashMap<Long, StreamBlock>(DEFAULT_CACHE_SIZE_IN_BLOCKS);
    this.statistics =
        new StreamStatistics(NFSBufferedInputStream.class + pathString,
            streamId.getAndIncrement(), true);
    this.pool = new TaskPool(poolSize);

    // Keep track of the file length at file open
    // NOTE: The file does not get modified while this stream is open
    Nfs3FileAttributes attributes =
        store.getFileAttributes(handle, store.getCredentials());
    if (attributes != null) {
      this.fileLength = attributes.getSize();
      if (this.fileLength < 0) {
        throw new IOException("File length is invalid: " + this.fileLength);
      }
    } else {
      throw new IOException("Could not get file length from NFS server");
    }

  }

  @Override
  public synchronized void seek(long pos) throws IOException {
    if (pos > fileLength) {
      throw new IOException("Cannot seek after EOF: pos=" + pos
          + ", fileLength=" + fileLength);
    }
    fileOffset = pos;
    prefetchBlockLimit =
        (long) (Math.min(fileLength, pos + this.splitSize) >> readBlockSizeBits);
  }

  @Override
  public synchronized long getPos() throws IOException {
    return fileOffset;
  }

  @Override
  public synchronized boolean seekToNewSource(long targetPos)
      throws IOException {
    return false;
  }

  @Override
  public synchronized int read() throws IOException {
    byte[] data = new byte[1];
    read(data, 0, 1);
    return (int) data[0];
  }

  @Override
  public synchronized int read(byte data[]) throws IOException {
    return read(data, 0, data.length);
  }

  @Override
  public synchronized int read(byte data[], int offset, int length)
      throws IOException {
    long enterTime = System.currentTimeMillis();
    int bytesRead = -1;

    try {
      bytesRead = _read(data, offset, length);
    } finally {
      if (bytesRead >= 0) {
        statistics.incrementBytesRead(bytesRead);
        statistics.incrementReadOps(1);
        statistics.incrementTimeRead(System.currentTimeMillis() - enterTime);
      }
    }
    return bytesRead;
  }

  private synchronized int _read(byte data[], int offset, int length)
      throws IOException {

    int lengthToRead = Math.min(data.length, length);
    int blockSize = (int) (1 << readBlockSizeBits);
    long loBlockId = (long) (fileOffset >> readBlockSizeBits);
    long hiBlockId =
        (long) ((fileOffset + lengthToRead - 1) >> readBlockSizeBits);
    int loOffset = (int) (fileOffset - (loBlockId << readBlockSizeBits));
    int hiOffset =
        (int) ((fileOffset + lengthToRead - 1) - (hiBlockId << readBlockSizeBits));

    if (closed.get() == true) {
      throw new IOException("Stream is already closed");
    }

    if (loBlockId == hiBlockId) {
      StreamBlock block = getBlock(loBlockId);
      if (block == null) {
        return -1;
      } else {
        int bytesRead =
            block.readFromBlock(data, offset, loOffset, lengthToRead);
        if (bytesRead != -1) {
          fileOffset += bytesRead;
        }
        return bytesRead;
      }
    } else {
      int totalBytesRead = offset;
      for (long blk = loBlockId; blk <= hiBlockId; blk++) {
        StreamBlock block = getBlock(blk);
        if (block == null) {
          if (blk == loBlockId) {
            return -1;
          } else {
            return (totalBytesRead - offset);
          }
        }

        if (blk == loBlockId) {
          int bytesRead =
              block.readFromBlock(data, totalBytesRead, loOffset, blockSize
                  - loOffset);
          if (bytesRead == -1) {
            return -1;
          }
          totalBytesRead += bytesRead;
          fileOffset += bytesRead;
        } else if (blk == hiBlockId) {
          int bytesRead =
              block.readFromBlock(data, totalBytesRead, 0, hiOffset + 1);
          if (bytesRead != -1) {
            totalBytesRead += bytesRead;
            fileOffset += bytesRead;
          }
        } else {
          int bytesRead =
              block.readFromBlock(data, totalBytesRead, 0, blockSize);
          if (bytesRead != -1) {
            totalBytesRead += bytesRead;
            fileOffset += bytesRead;
          } else {
            break;
          }
        }
      }
      return (totalBytesRead - offset);
    }
  }

  private StreamBlock getBlock(long blockId) throws IOException {

    // Block is current
    if (currentBlock != null && currentBlock.getBlockId() == blockId) {
      return currentBlock;
    }

    // Issue prefetches
    if (doPrefetch) {
      for (long bid = blockId + 1; bid < blockId + DEFAULT_READAHEAD_SIZE; ++bid) {
        if (!ongoing.containsKey(bid) && !cache.containsKey(bid)) {
          StreamBlock block = new StreamBlock(readBlockSizeBits);
          block.setBlockId(bid);
          block.setReady(false);
          cache.put(bid, block);

          ReadTask task = new ReadTask(store, handle, statistics, bid, block);
          ongoing.put(bid, task);
          pool.submitAny(task);
          LOG.debug("Submitted read task id=" + task.getTaskId()
              + " as prefetch for block id=" + bid);
        }
      }
    }

    // Block is being fetched, so wait for it
    if (ongoing.containsKey(blockId)) {
      StreamTask task = ongoing.get(blockId);
      LOG.debug("Waiting for read task id=" + task.getTaskId()
          + " to complete reading block id=" + blockId);
      task.await();
    }

    // Some prefetches are done, check for them
    for (Iterator<Entry<Long, StreamTask>> iter = ongoing.entrySet().iterator(); iter
        .hasNext();) {
      StreamTask task = iter.next().getValue();
      if (task.isDone()) {
        if (task.hasError()) {
          LOG.error("Read task id=" + task.getTaskId()
              + " resulted in error. Exception was " + task.getError());
          throw new IOException(task.getError().getCause());
        } else {
          iter.remove();
        }
      }
    }

    // Keep trying until the block is found
    while (true) {

      if (cache.containsKey(blockId)) {
        StreamBlock block = cache.remove(blockId);
        assert (block != null);
        assert (block.getBlockId() == blockId);
        assert (block.isReady() == true);
        currentBlock = block;
        return currentBlock;
      }

      if (cache.size() >= DEFAULT_CACHE_SIZE_IN_BLOCKS) {
        LOG.error("Cache is bigger than planned, size=" + cache.size()
            + " limit=" + DEFAULT_CACHE_SIZE_IN_BLOCKS);
      }

      // Issue the read and wait
      StreamBlock block = new StreamBlock(readBlockSizeBits);
      block.setBlockId(blockId);
      block.setReady(false);
      cache.put(blockId, block);

      ReadTask task = new ReadTask(store, handle, statistics, blockId, block);
      pool.submitAny(task);
      LOG.debug("Waiting for read task id=" + task.getTaskId()
          + " to complete reading block id=" + blockId);
      task.await();
    }

  }

  @Override
  public void close() throws IOException {
    if (closed.get() == true) {
      throw new IOException("Stream is already closed");
    }
    closed.set(true);

    List<StreamTask> failed = pool.shutdown();
    if (failed != null && !failed.isEmpty()) {
      LOG.error("Input stream has " + failed.size() + " tasks:");
      for (StreamTask task : failed) {
        LOG.error("Leftover task id=" + task.getTaskId() + " type="
            + task.getOperation() + " done=" + task.isDone() + " error="
            + task.hasError());
      }
      throw new IOException(
          "Some tasks left in the task pool. Check your data!");
    }
    LOG.info(statistics);
    super.close();
  }

}

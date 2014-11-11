package org.apache.hadoop.fs.nfsv3.server;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.nfs.NfsTime;
import org.apache.hadoop.nfs.nfs3.Nfs3FileAttributes;
import org.apache.hadoop.nfs.nfs3.request.SetAttr3;
import org.apache.hadoop.nfs.nfs3.response.WccAttr;

public abstract class FsObject implements Comparable<FsObject> {

  String name;
  long id;
  byte[] data;
  Nfs3FileAttributes attr; 
  FileType type;
  
  public static final int MAX_FILE_SIZE = 2*1024*1024;
  public static final AtomicLong fileIdCounter;
  
  static {
    fileIdCounter = new AtomicLong(1);
  }
  
  public enum FileType {TYPE_DIRECTORY, TYPE_FILE};
  
  protected FsObject(FileType type, String name, long id) {
    this.type = type;
    this.name = name;
    this.id = (id <= 0L ? fileIdCounter.getAndIncrement() : id);
    data = new byte[MAX_FILE_SIZE];
    attr = new Nfs3FileAttributes();
  }
  
  protected FsObject(FileType type, String name) {
    this(type, name, 0L);
  }
  
  public void setFilename(String filename) {
    this.name = filename;
  }
  
  public String getFilename() {
    return name;
  }

  public abstract void setAttr(SetAttr3 attr);
  
  public Nfs3FileAttributes getAttr() {
    return attr;
  }
  
  public WccAttr getWccAttr() {
    return new WccAttr(getAttr().getSize(), new NfsTime(getAttr().getMtime()), new NfsTime(getAttr().getCtime()));
  }
  
  public FileType getType() {
    return type;
  }
  
  public long getId() {
    return id;
  }

  @Override
  public int compareTo(FsObject other) {
    if(other.getType() == this.type) {
      return (int) (this.getId() - other.getId());
    }
    return -1;
  }
  
}

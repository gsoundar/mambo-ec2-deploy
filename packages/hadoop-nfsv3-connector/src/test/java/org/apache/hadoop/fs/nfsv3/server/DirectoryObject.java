package org.apache.hadoop.fs.nfsv3.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.nfs.NfsFileType;
import org.apache.hadoop.nfs.nfs3.Nfs3FileAttributes;
import org.apache.hadoop.nfs.nfs3.request.SetAttr3;

public class DirectoryObject extends FsObject {
  
  final Set<FsObject> contents;
  
  protected DirectoryObject(String name) {
    super(FileType.TYPE_DIRECTORY, name);
    contents = new HashSet<FsObject>();
  }
  
  protected DirectoryObject(String name, long id) {
    super(FileType.TYPE_DIRECTORY, name, id);
    contents = new HashSet<FsObject>();
  }

  public Set<FsObject> listDirectoryContents() {
    return contents;
  }
  
  @Override
  public void setAttr(SetAttr3 attr) {
    NfsFileType nfsType = (this.type.equals(FileType.TYPE_DIRECTORY)) ? NfsFileType.NFSDIR : NfsFileType.NFSREG;
    int nlink = 0;
    short mode = (short) attr.getMode();
    int uid = attr.getUid();
    int gid = attr.getGid();
    int size = 4096;
    int fsid = MockNfs3Filesystem.MOCK_FSID;
    int fileId = (int) this.getId();
    int mtime = (int) System.currentTimeMillis();
    if(attr.getMtime() != null) {
      mtime = attr.getMtime().getSeconds();
    }
    int atime = (int) System.currentTimeMillis();
    if(attr.getAtime() != null) {
      atime = attr.getAtime().getSeconds();
    }
    this.attr = new Nfs3FileAttributes(nfsType, nlink, mode, uid, gid, size, fsid, fileId, mtime, atime);
  }
  
  public FsObject getItemInDirectory(String name) {
    for(FsObject obj : contents) {
      if(obj.getFilename().equals(name)) {
        return obj;
      }
    }
    return null;
  }
  
  public void addNewFile(FileObject file) throws IOException {
    if(contents.contains(file)) {
      throw new IOException("Directory " + this + " already contains file " + file);
    } else {
      contents.add(file);
    }
  }
  
  public void addNewDirectory(DirectoryObject dir) throws IOException {
    if(contents.contains(dir)) {
      throw new IOException("Directory " + this + " already contains directory " + dir);
    } else {
      contents.add(dir);
    }
  }
  
  public void removeFileFromDirectory(FileObject file) throws IOException {
    assert(contents.contains(file));
    contents.remove(file);
  }
  
  public void removeDirectoryFromDirectory(DirectoryObject dir) throws IOException {
    assert(contents.contains(dir));
    assert(dir.listDirectoryContents().size() == 0);
    contents.remove(dir);
  }
  
}

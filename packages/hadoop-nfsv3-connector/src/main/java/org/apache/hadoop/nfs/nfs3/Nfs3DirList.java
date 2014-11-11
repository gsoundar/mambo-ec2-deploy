package org.apache.hadoop.nfs.nfs3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Nfs3DirList {
  final List<Nfs3DirEntry> entries;
  final boolean eof;
  final long cookieVerf;
  
  public static class Nfs3DirEntry {
    private final long fileId;
    private final String name;
    private final long cookie;
    
    public Nfs3DirEntry(long fileId, String name, long cookie) {
      this.fileId = fileId;
      this.name = name;
      this.cookie = cookie;
    }

    public long getFileId() {
      return fileId;
    }

    public String getName() {
      return name;
    }

    public long getCookie() {
      return cookie;
    }
  }
  
  public Nfs3DirList(Nfs3DirEntry[] entries, long cookieVerf, boolean eof) {
    this.entries = Collections.unmodifiableList(Arrays.asList(entries));
    this.eof = eof;
    this.cookieVerf = cookieVerf;
  }
  
  public List<Nfs3DirEntry> getEntries() {
    return this.entries;
  }
  
  public boolean isEof() {
    return eof;
  }
  
  public long getCookieVerf() {
    return cookieVerf;
  }
  
}

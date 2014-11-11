package org.apache.hadoop.fs.nfs.mount;

import java.io.IOException;

import org.apache.hadoop.mount.MountResponse;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3Status;
import org.apache.hadoop.oncrpc.XDR;

public class MountMNTResponse {
  int status;
  FileHandle filehandle;
  int []authFlavor;

  public MountMNTResponse(int returnValue, FileHandle filehandle, int[] authFlavor) {
    this.status = returnValue;
    this.filehandle = filehandle;
    this.authFlavor = authFlavor;
  }

  public MountMNTResponse(XDR xdr) throws IOException {
    status = xdr.readInt();
    
    if (status == MountResponse.MNT_OK) {
      filehandle = new Nfs3FileHandle();
      filehandle.deserialize(xdr);
      
      int flavorNum = xdr.readInt();
      if (flavorNum > 0) {
        authFlavor = new int[flavorNum];
        for(int i = 0; i < flavorNum; i++) {
          authFlavor[i] = xdr.readInt();
        }
      }
    } else {
      filehandle = null;
    }
  }

  public int getStatus() {
    return status;
  }

  public FileHandle getFilehandle() {
    return filehandle;
  }

  public int[] getAuthFlavors() {
    return authFlavor;
  }

}

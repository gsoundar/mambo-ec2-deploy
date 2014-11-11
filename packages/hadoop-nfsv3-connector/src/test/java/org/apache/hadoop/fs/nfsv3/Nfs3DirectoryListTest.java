package org.apache.hadoop.fs.nfsv3;

import static org.junit.Assert.*;

import java.net.BindException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.nfs.NFSFileSystemStore;
import org.apache.hadoop.fs.nfs.NFSRequestBuilder;
import org.apache.hadoop.fs.nfs.NFSResponseBuilder;
import org.apache.hadoop.fs.nfs.mount.MountClient;
import org.apache.hadoop.fs.nfs.mount.MountMNTResponse;
import org.apache.hadoop.fs.nfs.rpc.RpcClientTcp;
import org.apache.hadoop.fs.nfsv3.server.FileObject;
import org.apache.hadoop.fs.nfsv3.server.MockNfs3Filesystem;
import org.apache.hadoop.fs.nfsv3.server.MockNfs3Server;
import org.apache.hadoop.mount.MountInterface;
import org.apache.hadoop.nfs.NfsFileType;
import org.apache.hadoop.nfs.nfs3.FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3Constant;
import org.apache.hadoop.nfs.nfs3.Nfs3Constant.WriteStableHow;
import org.apache.hadoop.nfs.nfs3.Nfs3DirList;
import org.apache.hadoop.nfs.nfs3.Nfs3DirList.Nfs3DirEntry;
import org.apache.hadoop.nfs.nfs3.Nfs3FileAttributes;
import org.apache.hadoop.nfs.nfs3.Nfs3FileHandle;
import org.apache.hadoop.nfs.nfs3.Nfs3SetAttr;
import org.apache.hadoop.nfs.nfs3.Nfs3Status;
import org.apache.hadoop.nfs.nfs3.response.CREATE3Response;
import org.apache.hadoop.nfs.nfs3.response.FSINFO3Response;
import org.apache.hadoop.nfs.nfs3.response.GETATTR3Response;
import org.apache.hadoop.nfs.nfs3.response.LOOKUP3Response;
import org.apache.hadoop.nfs.nfs3.response.MKDIR3Response;
import org.apache.hadoop.nfs.nfs3.response.READ3Response;
import org.apache.hadoop.nfs.nfs3.response.READDIR3Response;
import org.apache.hadoop.nfs.nfs3.response.READDIR3Response.Entry3;
import org.apache.hadoop.nfs.nfs3.response.WRITE3Response;
import org.apache.hadoop.oncrpc.RpcAcceptedReply;
import org.apache.hadoop.oncrpc.RpcAcceptedReply.AcceptState;
import org.apache.hadoop.oncrpc.RpcMessage;
import org.apache.hadoop.oncrpc.RpcReply.ReplyState;
import org.apache.hadoop.oncrpc.XDR;
import org.apache.hadoop.oncrpc.security.CredentialsNone;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nfs3DirectoryListTest {

  static final int NFS_SERVER_PORT = 8211;
  static final String MOUNT_PATH = "/somepath";
  static NFSFileSystemStore store;
  static MockNfs3Server nfsServer;
  static Thread serverThread;
  static int nfsServerPort;
  
  public final static Logger LOG = LoggerFactory.getLogger(Nfs3DirectoryListTest.class);
  
  @BeforeClass
  public static void setUp() throws Exception {
    
    // Start the Mock NFS server
    nfsServerPort = NFS_SERVER_PORT;
    while(nfsServerPort < 50000) {
      try {
        nfsServer = new MockNfs3Server(false, nfsServerPort);
        serverThread = new Thread(nfsServer);
        serverThread.start();
        LOG.info("Started mock NFS3 server ...");
        break;
      } catch(BindException exception) {
        nfsServerPort++;
        continue;
      }
    }

    // Connect to NFS
    Configuration conf = new Configuration();
    conf.setBoolean("mambo.test", true);
    conf.set("mambo.test.mountpath", MOUNT_PATH);
    store = new NFSFileSystemStore(new URI("nfs://localhost:" + nfsServerPort + "/"), conf);
    store.initialize(new URI("nfs://localhost:" + nfsServerPort + "/"), conf);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    serverThread.stop();
    
  }

  @Test
  public void testREADDIR() throws Exception {
    
    FileHandle rootHandle = store.getRootfh();
    
    // Create some files
    Set<String> files = new HashSet<String>();
    for(int i = 0; i < 100; ++i) {
      CREATE3Response nfsCREATEResponse = store.create(rootHandle, "test" + i, Nfs3Constant.CREATE_UNCHECKED, new Nfs3SetAttr(), 0L, new CredentialsNone());
      assertEquals(Nfs3Status.NFS3_OK, nfsCREATEResponse.getStatus());
      files.add("test" + i);
    }
    
    // List directory contents
    READDIR3Response nfsREADDIRResponse = store.readdir(rootHandle, 0L, 0L, 65536, new CredentialsNone());
    assertEquals(Nfs3Status.NFS3_OK, nfsREADDIRResponse.getStatus());
    assertEquals(5, nfsREADDIRResponse.getDirList().getEntries().size());
    
    // List directory contents
    Nfs3DirList dirList = store.getDirectoryList(rootHandle, 0L, 0L, 65536, new CredentialsNone());
    assertEquals(5, dirList.getEntries().size());
    
    for(Nfs3DirEntry entry : dirList.getEntries()) {
      assertEquals(true, files.contains(entry.getName()));
    }
    
    // Bad handle
    READDIR3Response nfsREADDIRResponse2 = store.readdir(new Nfs3FileHandle(0L), 0L, 0L, 65536, new CredentialsNone());
    assertEquals(Nfs3Status.NFS3ERR_BADHANDLE, nfsREADDIRResponse2.getStatus());
    Nfs3DirList dirList2 = store.getDirectoryList(new Nfs3FileHandle(0L), 0L, 0L, 65536, new CredentialsNone());
    assertNull(dirList2);
    
  }
  
}

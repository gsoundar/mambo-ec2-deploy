package org.apache.hadoop.fs.nfsv3.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MockNfs3Server implements Runnable {

  final int serverPort;
  final ExecutorService threadPool;
  final MockNfs3Filesystem filesystem;
  final ServerSocket serverSocket;
  final boolean broken;
  
  public final static Logger LOG = LoggerFactory.getLogger(MockNfs3Server.class);
  
  public MockNfs3Server(boolean broken, int port) throws IOException {
    threadPool = Executors.newFixedThreadPool(128);
    serverPort = port;
    filesystem = new MockNfs3Filesystem();
    serverSocket = new ServerSocket(serverPort);
    this.broken = broken;
  }

  @Override
  public void run() {
    try {
      int threadId = 1;
      while(!serverSocket.isClosed()) {
        Socket clientSocket = serverSocket.accept();
        LOG.info("Launched NFS handler thread " + threadId);
        threadPool.execute(new MockNfs3ServerHandler(filesystem, clientSocket, broken));
        threadId++;
      }
      threadPool.shutdown();
    } catch(IOException exception) {
      exception.printStackTrace();
      LOG.error("Got an IOException in the accept loop");
    }
  }
  
}

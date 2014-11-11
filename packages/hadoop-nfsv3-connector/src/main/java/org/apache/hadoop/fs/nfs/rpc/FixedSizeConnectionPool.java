package org.apache.hadoop.fs.nfs.rpc;

import java.io.IOException;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class FixedSizeConnectionPool implements ConnectionPool {

	private final Queue<Socket> connections;
	
	public final static int DEFAULT_CONNECTION_NUM = 32;
	public final static int MUTEX_TIMEOUT_SECS = 10;
  private Semaphore connectionLock;
	
	public final static Log LOG = LogFactory.getLog(FixedSizeConnectionPool.class);

	public FixedSizeConnectionPool(String hostname, int port, int numConnections) throws IOException {
		
	  numConnections = Math.min(Math.max(1, numConnections), DEFAULT_CONNECTION_NUM);
		LOG.debug("creating a fixed size connection pool hostname=" + hostname + ", port=" + port);
		
		connections = new ConcurrentLinkedQueue<Socket>();
		for(int i = 0; i < numConnections; ++i) {
			Socket socket = new Socket(hostname, port);
			connections.add(socket);
		}
	  connectionLock = new Semaphore(numConnections);
	}


	@Override
	public Socket getSocket(int id) throws IOException, InterruptedException {
	  connectionLock.acquire();
		Socket socket = connections.poll();
		if(socket == null) {
			LOG.error("Could not get socket from available sockets. Ran out!");
			throw new IOException("Ran out of sockets in pool");
		}
		return socket;
		
	}


	@Override
	public void putSocket(int id, Socket socket) throws IOException {
		LOG.debug("putSocket() is called");
		assert(socket != null);    
    Boolean added = connections.offer(socket);
    if (added) {
      connectionLock.release();
    } else {
      LOG.warn("failed to return a socket to connection pool");
    }
		
	}

	@Override
	public void shutdown() throws IOException {
		Socket socket;
		while((socket = connections.poll()) != null) {
			socket.close();
		}
	}
}

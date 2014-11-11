package org.apache.hadoop.fs.nfs.rpc;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class SingleConnection implements ConnectionPool {

	public final static Log LOG = LogFactory.getLog(SingleConnection.class);
	
	private final Socket socket;
	private final Semaphore mutex;
	
	public SingleConnection(String hostname, int port) throws IOException {
		
		socket = new Socket(hostname, port);
		mutex = new Semaphore(1);
		
	}
	 
	@Override
	public Socket getSocket(int id) throws IOException {
		
		LOG.debug("Waiting for mutex to acquire socket from pool");
		boolean acquired = false;
		try {
			acquired = mutex.tryAcquire(60, TimeUnit.SECONDS);
			if(!acquired) {
				LOG.error("Could not get mutex for socket");
				throw new IOException("Could not get mutex for socket");
			}
			return socket;
		} catch (InterruptedException e) {
			throw new IOException("Could not get mutex for socket");
		}
		
	}

	@Override
	public void putSocket(int id, Socket socket) throws IOException {
		
		LOG.debug("Releasing mutex so socket can be used by someone else");
		mutex.release();
		
	}

	@Override
	public void shutdown() throws IOException {
		
		try {
			mutex.tryAcquire(60, TimeUnit.SECONDS);
			socket.close();
		} catch(InterruptedException e) {
			throw new IOException("Could not get mutex on socket pool");
		}
		
	}

}

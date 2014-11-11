package org.apache.hadoop.fs.nfs.rpc;

import java.io.IOException;
import java.net.Socket;

public interface ConnectionPool {

	public abstract Socket getSocket(int id) throws IOException, InterruptedException;

	public abstract void putSocket(int id, Socket socket) throws IOException;

	public abstract void shutdown() throws IOException;

}
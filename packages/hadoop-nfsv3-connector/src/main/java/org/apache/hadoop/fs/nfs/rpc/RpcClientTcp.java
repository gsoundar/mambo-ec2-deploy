package org.apache.hadoop.fs.nfs.rpc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.nfs.nfs3.Nfs3Status;
import org.apache.hadoop.oncrpc.RpcAcceptedReply;
import org.apache.hadoop.oncrpc.RpcCall;
import org.apache.hadoop.oncrpc.RpcMessage;
import org.apache.hadoop.oncrpc.RpcReply;
import org.apache.hadoop.oncrpc.XDR;
import org.apache.hadoop.oncrpc.security.Credentials;
import org.apache.hadoop.oncrpc.security.VerifierNone;
import org.jboss.netty.buffer.ChannelBuffer;

public class RpcClientTcp {

  private final ConnectionPool connectionPool;
  private final AtomicInteger xid;
  private final AtomicInteger socketCounter;

  public final static Log LOG = LogFactory.getLog(RpcClientTcp.class);

  public RpcClientTcp(String host, int port) {
    try {
      connectionPool = new SingleConnection(host, port);
      xid = new AtomicInteger(1);
      socketCounter = new AtomicInteger(0);
    } catch (IOException e) {
      LOG.error("Could not start up RPC client");
      throw new RuntimeException("Could not start up RPC client");
    }
  }
  
  public RpcClientTcp(String host, int port, int numConnections) {
    try {
      connectionPool = new FixedSizeConnectionPool(host, port, numConnections);
      xid = new AtomicInteger(1);
      socketCounter = new AtomicInteger(0);
    } catch (IOException e) {
      LOG.error("Could not start up RPC client");
      throw new RuntimeException("Could not start up RPC client");
    }
  }

  public RpcMessage service(int program, int version, int procedure, XDR in,
      XDR out, Credentials credentials) throws RpcException {
    Socket socket = null;
    int count = socketCounter.getAndIncrement();
    
    try {
      // Get a socket from the pool
      socket = connectionPool.getSocket(count);
      if (socket == null) {
        LOG.error("socket returned from the pool is null!");
        throw new RpcException("Could not get a free socket from pool");
      }
      assert(socket.isConnected());
      
      // Invoke the RPC
      RpcMessage reply =
          _service(program, version, procedure, in, out, credentials, socket);
      return reply;
    } catch (IOException e) {
      throw new RpcException("Got an IO exception while executing the RPC");
    } catch (InterruptedException e) {
      throw new RpcException(
          "Got an interrupt exception while trying to acquire connection lock");
    } finally {
      // Put the socket back to the pool
      try {
        if(socket != null) {
          connectionPool.putSocket(count, socket);
        }
      } catch(IOException exception) {
        throw new RpcException("Got an IO exception returning the socket back to the pool");
      }
    }
  }

  public void shutdown() {
    try {
      connectionPool.shutdown();
    } catch (IOException e) {
      LOG.error("Could not shutdown connection pool");
    }
  }

  public RpcMessage _service(int program, int version, int procedure, XDR in,
      XDR out, Credentials credentials, Socket socket) throws RpcException {

    int returnCode = Nfs3Status.NFS3_OK;
    int callXid = xid.getAndIncrement();

    // Build the RPC request
    XDR request = new XDR();
    RpcCall call =
        RpcCall.getInstance(callXid, program, version, procedure, credentials,
            new VerifierNone());
    call.write(request);
    request.writeFixedOpaque(in.getBytes());
    ChannelBuffer buf = XDR.writeMessageTcp(request, true);

    try {

      // Send the RPC to the server
      BufferedOutputStream oos =
          new BufferedOutputStream(socket.getOutputStream());
      oos.write(buf.toByteBuffer().array());
      oos.flush();
      LOG.debug("Message sent: prog=" + program + ", vers=" + version
          + ", proc=" + procedure + ", length="
          + buf.toByteBuffer().array().length);

      // Get the reply from the server
      BufferedInputStream ois =
          new BufferedInputStream(socket.getInputStream());
      byte[] reply = null;
      byte[] fragmentHeader = new byte[4];
      long totalReadBytes = 0;

      while (true) {

        // The first 4 bytes are the record header
        if (ois.read(fragmentHeader, 0, 4) != 4) {
          LOG.error("RPC xid=" + callXid + " Error to read record header\n");
          throw new RpcException("RPC: xid=" + callXid
              + " Could not read reply record header");
        }

        // The first bit is the flag to tell whether this is the last fragment
        // The left 31 bits are the length of this segment
        int fragmentHeaderInt = ByteBuffer.wrap(fragmentHeader).getInt();
        int lastFragmentFlag = fragmentHeaderInt & (1 << 31);
        int length = fragmentHeaderInt & (~(1 << 31));

        // Do multiple reads since a fragment could be larger than the maximal
        // allowed TCP packet
        byte[] fragment = new byte[length];
        int readbytes = 0;

        while (readbytes < length) {
          int curReadBytes = ois.read(fragment, readbytes, length - readbytes);
          readbytes += curReadBytes;
        }

        if (readbytes != length) {
          LOG.error("RPC: xid=" + callXid
              + " Read bytes not expected readbytes=" + readbytes + " length="
              + length);
          throw new RpcException("RPC: xid=" + callXid
              + " Bytes received did not match expected bytess");
        }
        totalReadBytes += readbytes;

        // Concatenate fragments together
        if (reply == null) {
          reply = fragment.clone();
        } else {
          reply = ArrayUtils.addAll(reply, fragment);
        }

        // Stop if we have reached the last fragment
        if (lastFragmentFlag != 0) {
          break;
        }
      }

      LOG.debug("RPC: xid=" + callXid + " Message received: length=" + totalReadBytes);
      XDR replyxdr = new XDR(reply);
      RpcReply rpcreply = RpcReply.read(replyxdr);

      /* Verify return status. */
      if (rpcreply.getState().equals(RpcReply.ReplyState.MSG_DENIED)) {
        LOG.error("RPC: xid=" + callXid + " RpcReply request denied: " + rpcreply);
        throw new RpcException("RPC: xid=" + callXid + " RpcReply request denied: " + rpcreply);
      }

      /* Verify xid in the reply message. */
      if (rpcreply.getXid() != callXid) {
        LOG.error("RPC: xid=" + callXid + "xid in rpcreply does not match: " + rpcreply.getXid() + "!="
            + callXid);
        throw new RpcException("RPC: xid=" + callXid + "xid in rpcreply does not match: " + rpcreply.getXid() + "!="
            + callXid);
      }

      RpcAcceptedReply acceptedReply = (RpcAcceptedReply) rpcreply;
      LOG.debug("RPC: xid=" + callXid + " RPC completed successfully with acceptstate=" + acceptedReply.getAcceptState());

      // store additional data to out
      out.writeFixedOpaque(replyxdr.getBytes());
      return acceptedReply;
      
    } catch (IOException e) {
      e.printStackTrace();
      throw new RpcException("RPC: xid=" + callXid + " RPC call failed due to an IOException=" + e.getMessage());
    }

  }
}

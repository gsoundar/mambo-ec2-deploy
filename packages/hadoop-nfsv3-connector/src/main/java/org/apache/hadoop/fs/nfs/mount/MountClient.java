package org.apache.hadoop.fs.nfs.mount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.nfs.rpc.RpcClientTcp;
import org.apache.hadoop.fs.nfs.rpc.RpcException;
import org.apache.hadoop.mount.MountEntry;
import org.apache.hadoop.mount.MountInterface;
import org.apache.hadoop.oncrpc.RpcAcceptedReply;
import org.apache.hadoop.oncrpc.RpcMessage;
import org.apache.hadoop.oncrpc.RpcAcceptedReply.AcceptState;
import org.apache.hadoop.oncrpc.XDR;
import org.apache.hadoop.oncrpc.security.Credentials;
import org.apache.hadoop.oncrpc.security.CredentialsNone;
import org.apache.hadoop.oncrpc.security.CredentialsSys;

public class MountClient extends RpcClientTcp {

  public static final int MOUNTD_PROGRAM = 100005;
  public static final int MOUNTD_VERSION = 3;

  public final static Log LOG = LogFactory.getLog(MountClient.class);
  private final Credentials credentials;

  public MountClient(String host, int port, Configuration conf) {
    super(host, port);

    // By default, we use AUTH_NONE for mount protocol, unless users specify
    // AUTH_SYS in the configuration file.
    if (conf != null) {
      String credentialsFlavor = conf.get("fs.nfs.mount.auth.flavor", null);

      if (credentialsFlavor != null
          && (credentialsFlavor.equalsIgnoreCase("AUTH_SYS") || credentialsFlavor
              .equalsIgnoreCase("AUTH_UNIX"))) {
        CredentialsSys sys = new CredentialsSys();
        sys.setUID(0);
        sys.setGID(0);
        sys.setStamp(new Long(System.currentTimeMillis()).intValue());
        credentials = sys;
      } else {
        credentials = new CredentialsNone();
      }
    } else {
      credentials = new CredentialsNone();
    }
  }

  public MountMNTResponse mnt(String path) throws IOException {
    try {
      XDR in = new XDR();
      XDR out = new XDR();
      RpcMessage reply;
      MountMNTResponse mountMNTResponse;

      // Construct MOUNT request
      in.writeString(path);

      reply =
          service(MOUNTD_PROGRAM, MOUNTD_VERSION,
              MountInterface.MNTPROC.MNT.getValue(), in, out,
              credentials);

      if (reply instanceof RpcAcceptedReply) {
        RpcAcceptedReply accepted = (RpcAcceptedReply) reply;
        LOG.debug("Mount MNT operation acceptState=" + accepted.getAcceptState());
        mountMNTResponse = new MountMNTResponse(out.asReadOnlyWrap());
        return mountMNTResponse;
      } else {
        LOG.error("Mount MNT operation was not accepted");
        throw new IOException("Mount MNT operation was not accepted");
      }
    } catch (RpcException exception) {
      LOG.error("Mount MNT operation failed with RpcException "
          + exception.getMessage());
      throw new IOException(exception.getCause());
    }
  }
}

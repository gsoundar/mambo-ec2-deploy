package org.apache.hadoop.fs.nfs.rpc;

public class RpcException extends Exception {

  private static final long serialVersionUID = 2246830929692053472L;

  public RpcException(String message) {
    super(message);
  }
  
}

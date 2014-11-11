package org.apache.hadoop.nfs.nfs3;

import org.apache.hadoop.nfs.NfsTime;

public class Nfs3Info {
  
  private final int rtmax;

  private final int rtpref;

  private final int rtmult;

  private final int wtmax;

  private final int wtpref;

  private final int wtmult;

  private final int dtpref;

  private final long maxFileSize;

  private final NfsTime timeDelta;

  private final int properties;
  
  public Nfs3Info(int rtmax,
      int rtpref, int rtmult, int wtmax, int wtpref, int wtmult, int dtpref,
      long maxFileSize, NfsTime timeDelta, int properties) {
    this.rtmax = rtmax;
    this.rtpref = rtpref;
    this.rtmult = rtmult;
    this.wtmax = wtmax;
    this.wtpref = wtpref;
    this.wtmult = wtmult;
    this.dtpref = dtpref;
    this.maxFileSize = maxFileSize;
    this.timeDelta = timeDelta;
    this.properties = properties;
  }

  public int getRtmax() {
    return rtmax;
  }

  public int getRtpref() {
    return rtpref;
  }

  public int getRtmult() {
    return rtmult;
  }

  public int getWtmax() {
    return wtmax;
  }

  public int getWtpref() {
    return wtpref;
  }

  public int getWtmult() {
    return wtmult;
  }

  public int getDtpref() {
    return dtpref;
  }

  public long getMaxFileSize() {
    return maxFileSize;
  }

  public NfsTime getTimeDelta() {
    return timeDelta;
  }

  public int getProperties() {
    return properties;
  }
  
}

package org.apache.hadoop.fs.nfs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.DelegateToFileSystem;
import org.apache.hadoop.nfs.nfs3.Nfs3Constant;

public class NFS extends DelegateToFileSystem {
	
	NFS(final URI theUri, final Configuration conf) throws IOException, URISyntaxException {
		super(theUri, new NFSFileSystem(), conf, theUri.getScheme(), false);
	}

	@Override
	public int getUriDefaultPort() {
		return Nfs3Constant.NFS3_SERVER_PORT_DEFAULT;
	}
}

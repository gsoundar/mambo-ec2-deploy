package org.apache.hadoop.fs.nfs.portmap;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.portmap.PortmapMapping;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PortMapTest {

  PortmapClient client;
  
  @Before
  public void setUp() throws Exception {
    client = new PortmapClient("localhost", 111);
  }

  @After
  public void tearDown() throws Exception {
    client.shutdown();
  }

  @Test
  public void testNull() {
    try {
      client.nullOp();
    } catch(IOException exception) {
      fail("Portmap NULL operation failed");
    }
  }
  
  @Test
  public void testGetPort() {
    try {
      int port = client.getport(PortmapClient.PROGRAM, PortmapClient.VERSION, PortmapMapping.TRANSPORT_TCP);
      assertEquals(111, port);
    } catch(IOException exception) {
      fail("Portmap GETPORT operation failed with exception=" + exception.getMessage());
    }
  }
  
  @Test
  public void testGetMapping() {
    try {
      
      List<PortmapMapping> programs = client.dump();
      assertNotNull(programs);
      
      // Should contains PORTMAP itself for sure
      boolean found = false;
      for(PortmapMapping prog : programs) {
        if(prog.toString().equals("(PortmapMapping-" + (PortmapClient.PROGRAM) + ":" + (PortmapClient.VERSION) + ":" + (PortmapMapping.TRANSPORT_TCP) + ":" + (111) + ")")) {
          found = true;
        }
      }
      assertTrue(found);
    } catch(IOException exception) {
      fail("Portmap DUMP operation failed with exception=" + exception.getMessage());
    }
  }

}

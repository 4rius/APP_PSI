package uk.arias.app_psi;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import uk.arias.app_psi.network.Node;
import uk.arias.app_psi.collections.DbConstants;

@RunWith(AndroidJUnit4.class)
public class NodeTest {
    private static Node node;

    @BeforeClass
    public static void setup() throws InterruptedException {
        ArrayList<String> peers = new ArrayList<>(Arrays.asList("192.168.1.2", "192.168.1.3"));
        node = Node.createNode("192.168.1.1", 5555, peers);
        Thread.sleep(3000);  // Wait for the node to fully start
    }


    @Test
    public void shouldAddNewDevice() {
        node.addPeer("192.168.1.4");
        assertTrue(node.getPeers().contains("192.168.1.4"));
    }

    @Test
    public void shouldNotAddExistingDevice() {
        node.addPeer("192.168.1.2");
        assertEquals(3, node.getPeers().size());
    }

    @Test
    public void shouldReturnId() {
        assertEquals("192.168.1.1", node.getId());
    }

    @Test
    public void shouldReturnFullId() {
        assertEquals("192.168.1.1", node.getFullId());
    }

    @Test
    public void shouldReturnPeers() {
        assertEquals(3, node.getPeers().size());
    }

    @Test
    public void shouldReturnRunningStatus() {
        assertTrue(node.isRunning());
    }

    @Test
    public void shouldReturnPort() {
        assertEquals(5555, node.getPort());
    }

    @Test
    public void shouldReturnMyData() {
        Assert.assertEquals(DbConstants.DFL_SET_SIZE, node.getMyData().size());
    }

    @Test
    public void shouldExtractIdFromIPv4() {
        assertEquals("192.168.1.2", node.extractId("192.168.1.2:5555"));
    }

    @Test
    public void shouldExtractIdFromIPv6() {
        assertEquals("2001:db8:85a3:8d3:1319:8a2e:370:7348", node.extractId("[2001:db8:85a3:8d3:1319:8a2e:370:7348]:5555"));
    }
}

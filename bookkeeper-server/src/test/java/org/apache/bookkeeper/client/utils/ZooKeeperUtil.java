package org.apache.bookkeeper.client.utils;


import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.bookkeeper.util.BookKeeperConstants.*;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.bookkeeper.util.IOUtils;
import org.apache.bookkeeper.zookeeper.ZooKeeperClient;
import org.apache.bookkeeper.zookeeper.ZooKeeperWatcherBase;
import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.test.ClientBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test the zookeeper utilities.
 */
public class ZooKeeperUtil {

    static {
        // org.apache.zookeeper.test.ClientBase uses FourLetterWordMain, from 3.5.3 four letter words
        // are disabled by default due to security reasons
        System.setProperty("zookeeper.4lw.commands.whitelist", "*");
    }
    static final Logger LOG = LoggerFactory.getLogger(ZooKeeperUtil.class);

    // ZooKeeper related variables
    protected Integer zooKeeperPort = 0;
    private InetSocketAddress zkaddr;

    protected ZooKeeperServer zks;
    protected ZooKeeper zkc; // zookeeper client
    protected NIOServerCnxnFactory serverFactory;
    protected File zkTmpDir;
    private String connectString;

    public ZooKeeperUtil() {
        String loopbackIPAddr = InetAddress.getLoopbackAddress().getHostAddress();
        zkaddr = new InetSocketAddress(loopbackIPAddr, 0);
        connectString = loopbackIPAddr + ":" + zooKeeperPort;
    }

    void expireSession(ZooKeeper zk) throws Exception {
        long id = zk.getSessionId();
        byte[] password = zk.getSessionPasswd();
        ZooKeeperWatcherBase w = new ZooKeeperWatcherBase(10000);
        ZooKeeper zk2 = new ZooKeeper(getZooKeeperConnectString(), zk.getSessionTimeout(), w, id, password);
        w.waitForConnection();
        zk2.close();
    }

    void createBKEnsemble(String ledgersPath) throws KeeperException, InterruptedException {
        Transaction txn = getZooKeeperClient().transaction();
        txn.create(ledgersPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        txn.create(ledgersPath + "/" + AVAILABLE_NODE, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        txn.create(ledgersPath + "/" + AVAILABLE_NODE + "/" + READONLY, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT);
        String instanceId = UUID.randomUUID().toString();
        txn.create(ledgersPath + "/" + INSTANCEID, instanceId.getBytes(UTF_8),
                ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        txn.commit();
    }


    public ZooKeeper getZooKeeperClient() {
        return zkc;
    }


    public String getZooKeeperConnectString() {
        return connectString;
    }


    public String getMetadataServiceUri() {
        return getMetadataServiceUri("/ledgers");
    }


    public String getMetadataServiceUri(String zkLedgersRootPath) {
        return "zk://" + connectString + zkLedgersRootPath;
    }


    public String getMetadataServiceUri(String zkLedgersRootPath, String type) {
        return "zk+" + type + "://" + connectString + zkLedgersRootPath;
    }


    public void startCluster() throws Exception {
        // create a ZooKeeper server(dataDir, dataLogDir, port)
        LOG.debug("Running ZK server");
        ClientBase.setupTestEnv();
        zkTmpDir = IOUtils.createTempDir("zookeeper", "test");

        // start the server and client.
        restartCluster();

        // create default bk ensemble
        createBKEnsemble("/ledgers");
    }


    public void restartCluster() throws Exception {
        zks = new ZooKeeperServer(zkTmpDir, zkTmpDir,
                ZooKeeperServer.DEFAULT_TICK_TIME);
        serverFactory = new NIOServerCnxnFactory();
        serverFactory.configure(zkaddr, 100);
        serverFactory.startup(zks);

        if (0 == zooKeeperPort) {
            zooKeeperPort = serverFactory.getLocalPort();
            zkaddr = new InetSocketAddress(zkaddr.getHostName(), zooKeeperPort);
            connectString = zkaddr.getHostName() + ":" + zooKeeperPort;
        }

        boolean b = ClientBase.waitForServerUp(getZooKeeperConnectString(),
                ClientBase.CONNECTION_TIMEOUT);
        LOG.debug("Server up: " + b);

        // create a zookeeper client
        LOG.debug("Instantiate ZK Client");
        zkc = ZooKeeperClient.newBuilder()
                .connectString(getZooKeeperConnectString())
                .sessionTimeoutMs(10000)
                .build();
    }

    public void sleepCluster(final int time,
                             final TimeUnit timeUnit,
                             final CountDownLatch l)
            throws InterruptedException, IOException {
        Thread[] allthreads = new Thread[Thread.activeCount()];
        Thread.enumerate(allthreads);
        for (final Thread t : allthreads) {
            if (t.getName().contains("SyncThread:0")) {
                Thread sleeper = new Thread() {
                    @SuppressWarnings("deprecation")
                    public void run() {
                        try {
                            t.suspend();
                            l.countDown();
                            timeUnit.sleep(time);
                            t.resume();
                        } catch (Exception e) {
                            LOG.error("Error suspending thread", e);
                        }
                    }
                };
                sleeper.start();
                return;
            }
        }
        throw new IOException("ZooKeeper thread not found");
    }


    public void stopCluster() throws Exception {
        if (zkc != null) {
            zkc.close();
        }

        // shutdown ZK server
        if (serverFactory != null) {
            serverFactory.shutdown();
            assertTrue("waiting for server down",
                    ClientBase.waitForServerDown(getZooKeeperConnectString(),
                            ClientBase.CONNECTION_TIMEOUT));
        }
        if (zks != null) {
            zks.getTxnLogFactory().close();
        }
    }


    public void killCluster() throws Exception {
        stopCluster();
        FileUtils.deleteDirectory(zkTmpDir);
    }
}

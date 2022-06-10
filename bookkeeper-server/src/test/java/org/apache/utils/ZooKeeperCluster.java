package org.apache.utils;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.bookkeeper.meta.LongHierarchicalLedgerManagerFactory;
import org.apache.bookkeeper.zookeeper.ZooKeeperClient;
import org.apache.zookeeper.*;
import org.apache.zookeeper.test.QuorumUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.bookkeeper.util.BookKeeperConstants.*;

public class ZooKeeperCluster {

        static {
            enableZookeeperTestEnvVariables();
        }

        static final Logger LOG = LoggerFactory.getLogger(ZooKeeperCluster.class);
        private final int numOfZKNodes;
        public QuorumUtil quorumUtil;
        String connectString;
        protected ZooKeeper zkc; // zookeeper client

        public static void enableZookeeperTestEnvVariables() {

            System.setProperty("zookeeper.4lw.commands.whitelist", "*");
            System.setProperty("zookeeper.admin.enableServer", "false");
            try {
                System.setProperty("build.test.dir", Files.createTempDirectory("zktests").toFile().getCanonicalPath());
            } catch (IOException e) {
                System.setProperty("build.test.dir", "/tmp");
            }
        }

        public ZooKeeperCluster(int numOfZKNodes){
            if ((numOfZKNodes < 3) || (numOfZKNodes % 2 == 0)) {
                throw new IllegalArgumentException("numOfZKNodes should be atleast 3 and it should not be even number");
            }
            this.numOfZKNodes = numOfZKNodes;
        }

        public String getZooKeeperConnectString() {
            return connectString;
        }

        public String getMetadataServiceUri() {
            return getMetadataServiceUri("/ledgers");
        }

        public String getMetadataServiceUri(String zkLedgersRootPath) {
            return getMetadataServiceUri(zkLedgersRootPath, LongHierarchicalLedgerManagerFactory.NAME);
        }

        public String getMetadataServiceUri(String zkLedgersRootPath, String type) {
            /*
             * URI doesn't accept ',', for more info. check
             * AbstractConfiguration.getMetadataServiceUri()
             */
            return "zk+" + type + "://" + connectString.replace(",", ";") + zkLedgersRootPath;
        }

        public ZooKeeper getZooKeeperClient() {
            return zkc;
        }

        public void startCluster() throws Exception {
            // QuorumUtil will start 2*n+1 nodes.
            quorumUtil = new QuorumUtil(numOfZKNodes / 2);
            quorumUtil.startAll();
            connectString = quorumUtil.getConnString();
            // create a zookeeper client
            LOG.debug("Instantiate ZK Client");
            zkc = ZooKeeperClient.newBuilder().connectString(getZooKeeperConnectString()).sessionTimeoutMs(10000).build();

            // create default bk ensemble
            createBKEnsemble("/ledgers");
        }

     public void createBKEnsemble(String ledgersPath) throws KeeperException, InterruptedException {
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

        public void stopCluster() throws Exception {
            if (zkc != null) {
                zkc.close();
            }
            quorumUtil.shutdownAll();
        }

        public void restartCluster() throws Exception {
            quorumUtil.startAll();
        }

        public void killCluster() throws Exception {
            quorumUtil.tearDown();
        }

        public void sleepCluster(int time, TimeUnit timeUnit, CountDownLatch l) throws InterruptedException, IOException {
            throw new UnsupportedOperationException("sleepServer operation is not supported for ZooKeeperClusterUtil");
        }
}

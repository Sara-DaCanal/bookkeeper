package org.apache.client.utils;

import org.apache.bookkeeper.bookie.*;
import org.apache.bookkeeper.common.allocator.ByteBufAllocatorWithOomHandler;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.discover.BookieServiceInfo;
import org.apache.bookkeeper.discover.RegistrationManager;
import org.apache.bookkeeper.meta.LedgerManager;
import org.apache.bookkeeper.meta.LedgerManagerFactory;
import org.apache.bookkeeper.meta.MetadataBookieDriver;
import org.apache.bookkeeper.meta.NullMetadataBookieDriver;
import org.apache.bookkeeper.net.BookieSocketAddress;
import org.apache.bookkeeper.proto.BookieServer;
import org.apache.bookkeeper.replication.Auditor;
import org.apache.bookkeeper.replication.AutoRecoveryMain;
import org.apache.bookkeeper.replication.ReplicationWorker;
import org.apache.bookkeeper.stats.StatsLogger;
import org.apache.bookkeeper.util.DiskChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.bookkeeper.bookie.BookKeeperServerStats.*;

public class ServerTester {
    static final Logger LOG = LoggerFactory.getLogger(ServerTester.class);
    private final ServerConfiguration conf;
    private final TestStatsProvider provider;
    private final Bookie bookie;
    private final BookieServer server;
    private final BookieSocketAddress address;
    private final MetadataBookieDriver metadataDriver;
    private final RegistrationManager registrationManager;
    private final LedgerManagerFactory lmFactory;
    private final LedgerManager ledgerManager;
    private final LedgerStorage storage;

    private AutoRecoveryMain autoRecovery;

    protected final ServerConfiguration baseConf = TestBKConfiguration.newServerConfiguration();
    private final ByteBufAllocatorWithOomHandler allocator = BookieResources.createAllocator(baseConf);

    public ServerTester(ServerConfiguration conf) throws Exception {
        this.conf = conf;
        provider = new TestStatsProvider();

        StatsLogger rootStatsLogger = provider.getStatsLogger("");
        StatsLogger bookieStats = rootStatsLogger.scope(BOOKIE_SCOPE);

        //metadataDriver = BookieResources.createMetadataDriver(conf, bookieStats);
        metadataDriver = new NullMetadataBookieDriver();
        registrationManager = metadataDriver.createRegistrationManager();
        lmFactory = metadataDriver.getLedgerManagerFactory();
        ledgerManager = lmFactory.newLedgerManager();

        /* LegacyCookieValidation cookieValidation = new LegacyCookieValidation(
                conf, registrationManager);
        cookieValidation.checkCookies(Main.storageDirectoriesFromConf(conf)); */

        DiskChecker diskChecker = BookieResources.createDiskChecker(conf);
        LedgerDirsManager ledgerDirsManager = BookieResources.createLedgerDirsManager(
                conf, diskChecker, bookieStats.scope(LD_LEDGER_SCOPE));
        LedgerDirsManager indexDirsManager = BookieResources.createIndexDirsManager(
                conf, diskChecker, bookieStats.scope(LD_INDEX_SCOPE), ledgerDirsManager);

        UncleanShutdownDetection uncleanShutdownDetection = new UncleanShutdownDetectionImpl(ledgerDirsManager);

        storage = BookieResources.createLedgerStorage(
                conf, ledgerManager, ledgerDirsManager, indexDirsManager,
                bookieStats, allocator);

        if (conf.isForceReadOnlyBookie()) {
            bookie = new ReadOnlyBookie(conf, registrationManager, storage,
                    diskChecker, ledgerDirsManager, indexDirsManager,
                    bookieStats, allocator, BookieServiceInfo.NO_INFO);
        } else {
            bookie = new BookieImpl(conf, registrationManager, storage,
                    diskChecker, ledgerDirsManager, indexDirsManager,
                    bookieStats, allocator, BookieServiceInfo.NO_INFO);
        }
        server = new BookieServer(conf, bookie, rootStatsLogger, allocator,
                uncleanShutdownDetection);
        address = BookieImpl.getBookieAddress(conf);

        autoRecovery = null;
    }

    public void startAutoRecovery() throws Exception {
        LOG.debug("Starting Auditor Recovery for the bookie: {}", address);
        autoRecovery = new AutoRecoveryMain(conf);
        autoRecovery.start();
    }

    void stopAutoRecovery() {
        if (autoRecovery != null) {
            LOG.debug("Shutdown Auditor Recovery for the bookie: {}", address);
            autoRecovery.shutdown();
        }
    }

    Auditor getAuditor() {
        if (autoRecovery != null) {
            return autoRecovery.getAuditor();
        } else {
            return null;
        }
    }

    ReplicationWorker getReplicationWorker() {
        if (autoRecovery != null) {
            return autoRecovery.getReplicationWorker();
        } else {
            return null;
        }
    }

    ServerConfiguration getConfiguration() {
        return conf;
    }

    public BookieServer getServer() {
        return server;
    }

    TestStatsProvider getStatsProvider() {
        return provider;
    }

    BookieSocketAddress getAddress() {
        return address;
    }

    void shutdown() throws Exception {
        server.shutdown();

        if (ledgerManager != null) {
            ledgerManager.close();
        }
        if (lmFactory != null) {
            lmFactory.close();
        }
        if (registrationManager != null) {
            registrationManager.close();
        }
        if (metadataDriver != null) {
            metadataDriver.close();
        }

        if (autoRecovery != null) {
            LOG.debug("Shutdown auto recovery for bookieserver: {}", address);
            autoRecovery.shutdown();
        }
    }
}

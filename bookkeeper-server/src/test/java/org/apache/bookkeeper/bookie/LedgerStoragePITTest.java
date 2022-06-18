package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.bookkeeper.client.utils.TestBKConfiguration;
import org.apache.bookkeeper.client.utils.TestStatsProvider;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.stats.StatsProvider;
import org.apache.bookkeeper.util.DiskChecker;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.apache.bookkeeper.bookie.BookKeeperServerStats.*;

@RunWith(Parameterized.class)
public class LedgerStoragePITTest {
    StatsProvider statsProvider = new TestStatsProvider();
    ServerConfiguration conf = TestBKConfiguration.newServerConfiguration();
    LedgerDirsManager ledgerDirsManager;
    InterleavedLedgerStorage interleavedStorage = new InterleavedLedgerStorage();
    private long ledgerId;
    private long entryId;
    private boolean expected;
    final long numOfEntries = 6;
    final long numOfLedgers = 2;

    public LedgerStoragePITTest(long ledgerId, long entryId, boolean expected) throws Exception {
        configure(ledgerId, entryId, expected);
    }

    @Parameterized.Parameters
    public static Collection parameters(){
        return Arrays.asList(new Object[][]{
                {-1, 0, true},
                {0, 0, false},
                {1, 0, false},
                {2, 0, true},

        });
    }

    CheckpointSource checkpointSource = new CheckpointSource() {
        @Override
        public Checkpoint newCheckpoint() {
            return Checkpoint.MAX;
        }

        @Override
        public void checkpointComplete(Checkpoint checkpoint, boolean compact) throws IOException {
        }
    };

    Checkpointer checkpointer = new Checkpointer() {
        @Override
        public void startCheckpoint(CheckpointSource.Checkpoint checkpoint) {
            // No-op
        }

        @Override
        public void start() {
            // no-op
        }
    };

    private ByteBuf createEntry(long lId, long eId){
        ByteBuf entry = Unpooled.buffer(128);
        entry.writeLong(lId);
        entry.writeLong(eId);
        entry.writeBytes(("entry-" + eId).getBytes());
        return entry;
    }

    private void configure(long ledgerId, long entryId, boolean expected) throws Exception {
        File tmpDir = File.createTempFile("bkTest", ".dir");
        tmpDir.delete();
        tmpDir.mkdir();
        File curDir = BookieImpl.getCurrentDirectory(tmpDir);
        BookieImpl.checkDirectoryStructure(curDir);

        conf.setLedgerDirNames(new String[]{tmpDir.toString()});
        ledgerDirsManager = new LedgerDirsManager(conf, conf.getLedgerDirs(),
                new DiskChecker(conf.getDiskUsageThreshold(), conf.getDiskUsageWarnThreshold()));

        EntryLogger entryLogger = new EntryLogger(TestBKConfiguration.newServerConfiguration());
        interleavedStorage.initializeWithEntryLogger(
                conf, null, ledgerDirsManager, ledgerDirsManager, entryLogger,
                statsProvider.getStatsLogger(BOOKIE_SCOPE));
        interleavedStorage.setCheckpointer(checkpointer);
        interleavedStorage.setCheckpointSource(checkpointSource);
        interleavedStorage.setMasterKey(0, "ledger_0".getBytes());
        interleavedStorage.setFenced(0);
        for (long eId = 0; eId < numOfEntries; eId++) {
            for (long lId = 0; lId < numOfLedgers; lId++) {
                if (eId == 0) {
                    interleavedStorage.setMasterKey(lId, ("ledger-" + lId).getBytes());
                    interleavedStorage.setFenced(lId);
                }
                ByteBuf entry = createEntry(lId, eId);

                interleavedStorage.addEntry(entry);
            }
        }

        this.ledgerId = ledgerId;
        this.entryId = entryId;
        this.expected = expected;
    }

    @Test
    public void test() {
        try {
            boolean exist = interleavedStorage.ledgerExists(ledgerId);
            Assert.assertNotEquals(expected, exist);
        } catch (IOException e) {
            Assert.fail();
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        interleavedStorage.shutdown();
    }
}

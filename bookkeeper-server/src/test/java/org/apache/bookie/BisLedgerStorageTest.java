package org.apache.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.bookkeeper.bookie.*;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.proto.BookieProtocol;
import org.apache.bookkeeper.util.DiskChecker;
import org.apache.client.utils.TestBKConfiguration;
import org.apache.client.utils.TestStatsProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.apache.bookkeeper.bookie.CheckpointSource.Checkpoint;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.apache.bookkeeper.bookie.BookKeeperServerStats.BOOKIE_SCOPE;

@RunWith(Parameterized.class)
public class BisLedgerStorageTest {
    TestStatsProvider statsProvider = new TestStatsProvider();
    ServerConfiguration conf = TestBKConfiguration.newServerConfiguration();
    LedgerDirsManager ledgerDirsManager;
    InterleavedLedgerStorage interleavedStorage = new InterleavedLedgerStorage();
    final long numOfEntries = 6;
    final long numOfLedgers = 2;
    private long ledgerId;
    private long entryId;
    private boolean expected;

    public BisLedgerStorageTest(long ledgerId, long entryId) throws Exception {
        configure(ledgerId, entryId);
    }

    @Parameterized.Parameters
    public static Collection parameters(){
        return Arrays.asList(new Object[][]{
                {0, 1},
                {0, BookieProtocol.LAST_ADD_CONFIRMED}
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
        public void startCheckpoint(Checkpoint checkpoint) {
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

    private void configure(long ledgerId, long entryId) throws Exception {
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
        this.expected = true;
    }
    private File findFile(long logId) throws FileNotFoundException {
        for (File d : ledgerDirsManager.getAllLedgerDirs()) {
            File f = new File(d, Long.toHexString(logId) + ".log");
            if (f.exists()) {
                return f;
            }
        }
        throw new FileNotFoundException("No file for log " + Long.toHexString(logId));
    }

    static long logIdForOffset(long offset) {
        return offset >> 32L;
    }
    @Test
    public void test() {
        try{
            if(entryId == BookieProtocol.LAST_ADD_CONFIRMED) entryId = 5;
            LedgerCache ledgerCache = interleavedStorage.getLedgerCache();
            long location = ledgerCache.getEntryOffset(ledgerId, entryId);
            long entryLogId = logIdForOffset(location);
            File file = findFile(entryLogId);
            file.delete();
            ByteBuf result = interleavedStorage.getEntry(ledgerId, entryId);
            Assert.fail();
        }catch(IOException e){
            Assert.assertTrue(expected);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        interleavedStorage.shutdown();
    }
}

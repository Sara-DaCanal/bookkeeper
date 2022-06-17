package org.apache.bookkeeper.bookie;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.bookkeeper.proto.BookieProtocol;
import org.apache.bookkeeper.util.DiskChecker;
import org.apache.bookkeeper.client.utils.TestBKConfiguration;
import org.apache.bookkeeper.client.utils.TestStatsProvider;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.apache.bookkeeper.bookie.CheckpointSource.Checkpoint;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.apache.bookkeeper.bookie.BookKeeperServerStats.BOOKIE_SCOPE;

@RunWith(Parameterized.class)
public class TrisLedgerStorageTest {
    TestStatsProvider statsProvider = new TestStatsProvider();
    ServerConfiguration conf = TestBKConfiguration.newServerConfiguration();
    LedgerDirsManager ledgerDirsManager;
    InterleavedLedgerStorage interleavedStorage = new InterleavedLedgerStorage();
    private long ledgerId;
    private long entryId;
    private boolean expected;
    private boolean outOfBound;

    public TrisLedgerStorageTest(long ledgerId, long entryId, boolean expected, boolean outOfBound) throws Exception {
        configure(ledgerId, entryId, expected, outOfBound);
    }

    @Parameterized.Parameters
    public static Collection parameters(){
        return Arrays.asList(new Object[][]{

                {-1, -1, true, false},
                {-1, 0, true, false},
                {-1, 6, true, false},
                {-1, BookieProtocol.LAST_ADD_CONFIRMED, true, false},
                //{0, -1, false, true},
                {0,-2, true, true},
                {0, 1, true, false},
                {0, 6, true, false},
                {0, BookieProtocol.LAST_ADD_CONFIRMED, true, false},
                {2, -1, true, false},
                {2, 0, true, false},
                {2, 6, true, false},
                {2, BookieProtocol.LAST_ADD_CONFIRMED, true, false}

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

    private void configure(long ledgerId, long entryId, boolean expected, boolean outOfBound) throws Exception {
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

        this.ledgerId = ledgerId;
        this.entryId = entryId;
        this.expected = expected || outOfBound;
        this.outOfBound = outOfBound;
    }

    @Test
    public void test() {
        try{
            ByteBuf result = interleavedStorage.getEntry(ledgerId, entryId);
            if(expected==true) Assert.fail();
        }catch(IOException e){
            Assert.assertTrue(expected);
        }
        catch (IndexOutOfBoundsException e){
            Assert.assertTrue(outOfBound);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        interleavedStorage.shutdown();
    }
}


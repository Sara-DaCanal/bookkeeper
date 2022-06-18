package org.apache.bookkeeper.client;

import org.apache.bookkeeper.client.utils.BookKeeperClusterTestCase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.Collection;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

@RunWith(Parameterized.class)
public class LedgerHandleTest extends BookKeeperClusterTestCase {
    private LedgerHandle lh;
    private byte[] data;
    private int offset;
    private int length;
    private AsyncCallback.AddCallback cb;
    private Object ctx;
    private boolean isArrayIndexExceptionExpected;
    private boolean isNullPointerExpected;


    private static final byte[] TEST_LEDGER_PASSWORD = "testpasswd".getBytes();
    private static final Logger LOG = LoggerFactory.getLogger(LedgerHandleTest.class);


    public LedgerHandleTest(Data data, int offset, int length, Cb cb, Exc isExceptionExpected) throws Exception {
        super(3);
        configure(data, offset, length, cb, isExceptionExpected);
    }

    @Parameterized.Parameters
    public static Collection parameters(){
        return Arrays.asList(new Object[][]{
                {Data.NULL, 0, 1, Cb.VALID, Exc.NULL},
                {Data.VOID, 0, 1, Cb.VALID, Exc.OUT_OF_BOUND},
                {Data.LENGTH1, 0, 1, Cb.NULL, Exc.NULL},
                {Data.LENGTH1, 0, 1, Cb.VALID, Exc.NO_EXC},
                {Data.LENGTH1, -1, 0, Cb.VALID, Exc.OUT_OF_BOUND},
                {Data.LENGTH1, 0, -1, Cb.VALID, Exc.OUT_OF_BOUND},
                {Data.LENGTH1, 1, 1, Cb.VALID, Exc.OUT_OF_BOUND},
                {Data.LENGTH4, 0, 0, Cb.VALID, Exc.NO_EXC},
                {Data.LENGTH4, 1, 2, Cb.VALID, Exc.NO_EXC},
                {Data.LENGTH4, -1, 0, Cb.VALID, Exc.OUT_OF_BOUND},
                {Data.LENGTH4, 0, -1, Cb.VALID, Exc.OUT_OF_BOUND},
                {Data.LENGTH4, 0, 5, Cb.VALID, Exc.OUT_OF_BOUND}
        });
    }

    private void configure(Data data, int offset, int length, Cb cb, Exc isExceptionExpected) throws Exception {
        setUp();
        lh = bkc.createLedger(BookKeeper.DigestType.CRC32,
                TEST_LEDGER_PASSWORD);
        this.offset=offset;
        this.length=length;
        this.ctx = null;
        switch (data){
            case NULL:
                this.data = null;
                break;
            case LENGTH1:
                this.data = "a".getBytes();
                break;
            case LENGTH4:
                this.data = "abcd".getBytes();
                break;
            case VOID:
                this.data=new byte[]{};
                break;
        }
        switch (cb){
            case NULL:
                this.cb=null;
                break;
            case VALID:
                this.cb=getMockedAddCb();
                break;
        }
        switch (isExceptionExpected){
            case NO_EXC:
                this.isNullPointerExpected=false;
                this.isArrayIndexExceptionExpected=false;
                break;
            case OUT_OF_BOUND:
                this.isArrayIndexExceptionExpected=true;
                this.isNullPointerExpected=false;
                break;
            case NULL:
                this.isNullPointerExpected=true;
                this.isArrayIndexExceptionExpected=false;
                break;
        }
    }
    private AsyncCallback.AddCallback getMockedAddCb() {

        AsyncCallback.AddCallback cb = mock(AsyncCallback.AddCallback.class);
        doNothing().when(cb).addComplete(isA(Integer.class), isA(LedgerHandle.class), isA(Long.class),
                isA(Object.class));

        return cb;
    }

    @Test
    public void test() throws BKException, InterruptedException{
       try{
           if(data.length==1 && offset==0) bkc.close();
           lh.asyncAddEntry(this.data, this.offset, this.length, this.cb, this.ctx);
           Assert.assertFalse(isArrayIndexExceptionExpected || isNullPointerExpected);
       }catch (NullPointerException e){
           Assert.assertTrue(isNullPointerExpected);
       }catch (ArrayIndexOutOfBoundsException e) {
           Assert.assertTrue(isArrayIndexExceptionExpected);
       }
    }

    private enum Data{
        NULL, LENGTH1, VOID, LENGTH4
    }
    private enum Cb{
        NULL, VALID
    }
    private enum Exc{
        NO_EXC, OUT_OF_BOUND, NULL
    }



}

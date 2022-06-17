package org.apache.bookkeeper.bookie;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class TrisBookieStatusTest {

    BufferedReader br;
    BookieStatus bkstatus = new BookieStatus();
    int expected; //0 se nessuna eccezione attesa, 1 per IOException e 2 per IllegalArgumentException, 3 per NullPointerException
    boolean isNull;

    public TrisBookieStatusTest(String s, boolean isNull,  int expected){
        configure(s, isNull, expected);
    }

    @Parameterized.Parameters
    public static Collection parameters(){
        return Arrays.asList(new Object[][]{
                {",,,,", true, 0},
                {",", true, 0},
                {",1,READ_ONLY,1", false, 2},
                {"1,READ_ONLY,", true, 0},
                {"1,READ_ONLY,1,", false, 0}
        });
    }

    private void configure(String s, boolean isNull, int expected) {
        if(s == null) this.br=null;
        else {
            InputStream io = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
            Reader in = new InputStreamReader(io);
            this.br = new BufferedReader(in);
        }
        this.expected=expected;
        this.isNull=isNull;
    }
    @Test
    public void test(){
        try {
            BookieStatus newbkstatus = bkstatus.parse(br);
            Assert.assertEquals(0, expected);
            if(isNull) Assert.assertNull(newbkstatus);
            else Assert.assertNotNull(newbkstatus);
        } catch (IOException e) {
            Assert.assertEquals(1, expected);
        }catch (IllegalArgumentException e){
            Assert.assertEquals(2,expected);
        }catch (NullPointerException e) {
            Assert.assertEquals(3, expected);
        }
    }
}


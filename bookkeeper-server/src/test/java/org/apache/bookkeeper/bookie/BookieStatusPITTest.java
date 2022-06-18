package org.apache.bookkeeper.bookie;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class BookieStatusPITTest {
    private String s;
    private boolean expected;
    private BookieStatus bkstatus;

    public BookieStatusPITTest(String s, boolean expected){
        configure(s,expected);
    }

    private void configure(String s, boolean expected) {
        this.expected=expected;
        this.s=s;
        this.bkstatus=new BookieStatus();
    }

    @Parameterized.Parameters
    public static Collection parameters(){
        return Arrays.asList(new Object[][]{
                {"1,READ_ONLY,23", false},
                {"1,READ_WRITE,23", true}
        });
    }

    @Test
    public void test(){
        try {
            BookieStatus newbk = bkstatus.parse(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)))));
            Assert.assertEquals(expected, newbk.isInWritable());
        } catch (IOException e) {
            Assert.fail();
        }

    }
}

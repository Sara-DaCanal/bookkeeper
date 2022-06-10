package org.apache;

import org.apache.bookkeeper.conf.ServerConfiguration;
import org.apache.client.utils.ServerTester;
import org.apache.client.utils.TestBKConfiguration;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class TestServerUP {
    ServerTester server;

    private ServerTester startBookie(ServerConfiguration conf) throws Exception {

        ServerTester tester = new ServerTester(conf);
        tester.getServer().getBookie().getLedgerStorage().setMasterKey(1,
                "masterKey".getBytes(StandardCharsets.UTF_8));
        tester.getServer().start();
        return tester;

    }

    public TestServerUP() throws Exception {
            this.server = startBookie(TestBKConfiguration.newServerConfiguration());
    }
    @Test
    public void test(){
        System.out.println(this.server.getServer());
    }
}

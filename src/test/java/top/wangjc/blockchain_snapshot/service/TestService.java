package top.wangjc.blockchain_snapshot.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import top.wangjc.blockchain_snapshot.service.impl.BtcSnapshotServiceImpl;

@SpringBootTest
@RunWith(SpringRunner.class)
public class TestService {
    @Autowired
    private BtcSnapshotServiceImpl btcSnapshotService;

    @Test
    public void TestGetBtcBalance() {

        System.out.println(""+btcSnapshotService.getServiceStatus());
    }
}

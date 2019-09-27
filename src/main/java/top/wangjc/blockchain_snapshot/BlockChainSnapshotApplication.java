package top.wangjc.blockchain_snapshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import top.wangjc.blockchain_snapshot.service.impl.BtcSnapshotServiceImpl;
import top.wangjc.blockchain_snapshot.service.impl.ExtractAddressServiceImpl;
import top.wangjc.blockchain_snapshot.service.impl.LtcSnapshotServiceImpl;

@SpringBootApplication
public class BlockChainSnapshotApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(BlockChainSnapshotApplication.class, args);
//        EthSnapshotServiceImpl ethService = context.getBean(EthSnapshotServiceImpl.class);
//        ethService.start();
//        TronSnapshotServiceImpl tronService=context.getBean(TronSnapshotServiceImpl.class);
//        tronService.start();
//        BtcSnapshotServiceImpl btcSnapshotService = context.getBean(BtcSnapshotServiceImpl.class);
//        btcSnapshotService.start();
//        BtcBlockImportServiceImpl btcService = context.getBean(BtcBlockImportServiceImpl.class);
//        btcService.start();
//        BtcReplayServiceImpl btcService = context.getBean(BtcReplayServiceImpl.class);
//        btcService.start();
        LtcSnapshotServiceImpl ltcSnapshotService = context.getBean(LtcSnapshotServiceImpl.class);
        ltcSnapshotService.start();
//        ExtractAddressServiceImpl extractAddressService=context.getBean(ExtractAddressServiceImpl.class);
//        extractAddressService.start();
    }

}

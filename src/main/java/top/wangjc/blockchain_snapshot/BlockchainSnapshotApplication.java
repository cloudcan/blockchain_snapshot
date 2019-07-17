package top.wangjc.blockchain_snapshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import top.wangjc.blockchain_snapshot.service.impl.EthSnapshotServiceImpl;

@SpringBootApplication
@EnableTransactionManagement
public class BlockchainSnapshotApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(BlockchainSnapshotApplication.class, args);
        //
        EthSnapshotServiceImpl ethService = context.getBean(EthSnapshotServiceImpl.class);
        ethService.start();
    }

}

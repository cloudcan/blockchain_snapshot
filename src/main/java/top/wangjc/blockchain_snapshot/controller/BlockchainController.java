package top.wangjc.blockchain_snapshot.controller;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.wangjc.blockchain_snapshot.service.BlockChainService;
import top.wangjc.blockchain_snapshot.service.impl.AbstractSnapshotService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class BlockchainController implements ApplicationContextAware {


    private List<AbstractSnapshotService> services = new ArrayList<>();

    /**
     * 获取服务状态
     *
     * @return
     */
    @GetMapping("/status")
    ResponseEntity<List<AbstractSnapshotService>> getServiceStatus() {
        return ResponseEntity.ok().body(services);
    }

    @PostMapping("/start/{chainType}")
    ResponseEntity startService(@PathVariable("chainType") BlockChainService.ChainType chainType) {
        Optional<AbstractSnapshotService> find = services.stream().filter(serv -> serv.chainType == chainType).findAny();
        if (find.isPresent()) {
            find.get().start();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("not find service");
    }
    @PostMapping("/stop/{chainType}")
    ResponseEntity stopService(@PathVariable("chainType") BlockChainService.ChainType chainType) {
        Optional<AbstractSnapshotService> find = services.stream().filter(serv -> serv.chainType == chainType).findAny();
        if (find.isPresent()) {
            find.get().stop();
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("not find service");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, AbstractSnapshotService> serviceMap = applicationContext.getBeansOfType(AbstractSnapshotService.class);
        services.addAll(serviceMap.values());
    }
}

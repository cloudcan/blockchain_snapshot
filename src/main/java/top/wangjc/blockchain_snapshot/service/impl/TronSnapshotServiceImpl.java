package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Service;
import org.web3j.utils.Async;
import org.web3j.utils.Flowables;
import top.wangjc.blockchain_snapshot.dto.TronAccount;
import top.wangjc.blockchain_snapshot.dto.TronBlock;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;
import top.wangjc.blockchain_snapshot.entity.TronAccountEntity;
import top.wangjc.blockchain_snapshot.repository.OperationLogRepository;
import top.wangjc.blockchain_snapshot.repository.TronAccountRepository;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TronSnapshotServiceImpl extends AbstractSnapshotService implements ApplicationListener<ContextClosedEvent> {
    private long taskStartMill;
    @Value("${tron.httpAddr}")
    private String httpAddr;
    @Value("${tron.batchSize}")
    private int batchSize;
    @Value("${tron.startBlock:}")
    private BigInteger startBlock;
    @Value("${tron.endBlock:}")
    private BigInteger endBlock;

    public static ChainType chainType = ChainType.Tron;
    private static final String INVALID_ADDRESS = "3078303030303030303030303030303030303030303030";
    private final ScheduledExecutorService scheduledExecutorService = Async.defaultExecutorService();
    private Scheduler scheduler;
    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private TronAccountRepository tronAccountRepository;

    private EnhanceHttpServiceImpl httpService;
    // 找到的账户
    private Set<String> foundAddress = new ConcurrentSkipListSet<>();

    @Override
    public void start() {
        if (getServiceStatus() != ServiceStatus.Running) {
            setServiceStatus(ServiceStatus.Running);
            taskStartMill = System.currentTimeMillis();
            executeBlockSyncTask();
        } else {
            log.info("TronService is running!");
        }
    }

    /**
     * 区块同步任务
     */
    private void executeBlockSyncTask() {
        httpService = EnhanceHttpServiceImpl.createDefault(httpAddr);
        scheduler = Schedulers.from(scheduledExecutorService);
        // 获取当前区块高度
        BigInteger endBlockNumber = getEndBlock();
        // 获取任务起始区块
        BigInteger startBlockNumber = getStartBlock();
        // 重放区块
        replayBlock(startBlockNumber, endBlockNumber);
    }

    /**
     * 重放区块
     *
     * @param startBlockNumber
     * @param endBlockNumber
     */
    private void replayBlock(BigInteger startBlockNumber, BigInteger endBlockNumber) {
        try {

            log.info("============Tron start block number is:{},end block number is :{} =================== ", startBlockNumber, endBlockNumber);
            // 分批次遍历区块
            Flowables.range(startBlockNumber, endBlockNumber)
                    .buffer(100)
                    .parallel()
                    .runOn(scheduler)
                    .map(this::handleBatchBlocks)
                    .sequential()
                    .subscribe(this::handleLog, this::handleError, this::handleLogComplete);
        } catch (Exception e) {
            log.error("replayBlock throw Exception:", e);
        }
    }

    /**
     * 记录日志
     *
     * @param operationLogEntity
     */
    private void handleLog(OperationLogEntity operationLogEntity) {
        operationLogRepository.save(operationLogEntity);
    }

    /**
     * 处理错误
     */
    private void handleError(Throwable t) {
        log.error("error:", t);
    }

    /**
     * 日志处理完成
     */
    private void handleLogComplete() {
        log.info("================= replay  complete ,total cost:{} s,get {} address!===============", (System.currentTimeMillis() - taskStartMill) / 1000, foundAddress.size());
        stop();
    }

    /**
     * 获取批次区块信息
     *
     * @param batch
     * @return
     */
    private OperationLogEntity handleBatchBlocks(List<BigInteger> batch) {
        log.info("=======start handle {}-{} block ===========", batch.get(0), batch.get(batch.size() - 1));
        long s = System.currentTimeMillis();
        OperationLogEntity operationLogEntity = new OperationLogEntity(batch.get(0), batch.get(batch.size() - 1), chainType);
        Flowable.fromIterable(batch)
                .map(this::getBlockByNum)
                .retry(3)
                .map(block -> block.getTransactions())
                .flatMapIterable(transactions -> transactions)
                .map(this::getOwnerAddresses)
                .map(this::getAccount)
                .map(TronAccountEntity::fromTronAccount)
                .toList()
                .subscribe(tronAccountEntities -> {
                    tronAccountRepository.saveAll(tronAccountEntities);
                    log.info("======handle {}-{} batch block complete，get {} accounts ,cost {} s=======", batch.get(0), batch.get(batch.size() - 1), tronAccountEntities.size(), (System.currentTimeMillis() - s) / 1000);
                }, this::handleError);
        return operationLogEntity;
    }

    /**
     * 从交易中查找账号
     *
     * @param transaction
     * @return
     */
    private String getOwnerAddresses(TronBlock.Transaction transaction) {
        return transaction.getRawData().getContract().stream().map(contract -> contract.getParameter().getValue().getOwnerAddress()).collect(Collectors.toList()).get(0);
    }

    /**
     * 获得任务起始区块
     *
     * @return
     */
    private BigInteger getStartBlock() {
        BigInteger start = BigInteger.ZERO;
        if (startBlock == null) {
            OperationLogEntity lastLog = operationLogRepository.findLastLogByChainType(chainType);
            if (lastLog != null && lastLog.getEndBlock() != null) {
                start = lastLog.getEndBlock().add(BigInteger.ONE);
            }
        } else {
            start = startBlock;
        }
        return start;
    }

    /**
     * 获得任务结束区块
     *
     * @return
     */
    private BigInteger getEndBlock() {
        BigInteger end = BigInteger.ONE;
        if (endBlock == null) {
        } else {
            end = endBlock;
        }
        return end;
    }

    @Override
    public void stop() {
        if (getServiceStatus() != ServiceStatus.Stopped) {
            try {
                if (scheduler != null) {
                    scheduler.shutdown();
                }
                if (scheduledExecutorService != null) {
                    scheduledExecutorService.shutdown();
                }
                scheduledExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                setServiceStatus(ServiceStatus.Stopped);
                log.info("TronService will be stopped!");
            } catch (InterruptedException e) {
                log.error("stop EthService error:", e);
            }
        } else {
            log.info("TronService has been stopped!");
        }
    }

    /**
     * 获取账户信息
     *
     * @param address
     * @return
     */
    private TronAccount getAccount(String address) throws IOException {
        TronAccount account = new TronAccount(address);
        if (!INVALID_ADDRESS.equals(address)) {
            Request request = new Request.Builder().get().url(httpAddr + "/wallet/getaccount?address=" + address).build();
            account = httpService.sendCustomRequest(request, TronAccount.class);
        }
        return account;
    }

    private TronBlock getBlockByNum(BigInteger number) throws IOException {
        Request request = new Request.Builder().get().url(httpAddr + "/wallet/getblockbynum?num=" + number).build();
        return httpService.sendCustomRequest(request, TronBlock.class);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        stop();
    }
}

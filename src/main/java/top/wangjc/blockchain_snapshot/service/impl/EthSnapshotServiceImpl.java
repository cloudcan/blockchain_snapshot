package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.internal.operators.flowable.FlowableFromCallable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Async;
import org.web3j.utils.Flowables;
import org.web3j.utils.Numeric;
import top.wangjc.blockchain_snapshot.entity.EthAccountEntity;
import top.wangjc.blockchain_snapshot.entity.EthLogEntity;
import top.wangjc.blockchain_snapshot.repository.EthAccountRepository;
import top.wangjc.blockchain_snapshot.repository.EthLogRepository;
import top.wangjc.blockchain_snapshot.repository.EthTrxRepository;
import top.wangjc.blockchain_snapshot.repository.NativeSqlRepository;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EthSnapshotServiceImpl extends AbstractSnapshotService {

    @Value("${eth.rpcAddr}")
    private String rpcAddr;
    @Value("${eth.batchSize}")
    private int batchSize;
    @Value("${eth.startBlock:}")
    private BigInteger startBlock;
    @Value("${eth.endBlock:}")
    private BigInteger endBlock;

    private final ScheduledExecutorService scheduledExecutorService = Async.defaultExecutorService();
    // 批量处理区块数量
    private EnhanceHttpServiceImpl httpService;
    private Web3j web3Client;
    private Scheduler scheduler;
    private Map<String, EthAccountEntity> accounts = new ConcurrentHashMap<>();
    private long taskStartMill;


    @Autowired
    private EthTrxRepository ethTrxRepository;
    @Autowired
    private EthLogRepository ethLogRepository;
    @Autowired
    private EthAccountRepository ethAccountRepository;

    @Autowired
    private NativeSqlRepository nativeSqlRepository;

    /**
     * 重放区块
     */
    private void replayBlock(BigInteger startBlockNumber, BigInteger endBlockNumber) {
        try {

            log.info("============Ethereum start block number is:{},end block number is :{} =================== ", startBlockNumber, endBlockNumber);
            // 分批次遍历区块
            Flowables.range(startBlockNumber, endBlockNumber)
                    .buffer(batchSize)
                    .parallel()
                    .runOn(scheduler)
                    .map(this::handleBatchBlock)
                    .sequential()
                    .subscribe(this::handleLog, this::handleError, this::handleLogComplete);
        } catch (Exception e) {
            log.error("replayBlock throw Exception:", e);
        }
    }

    /**
     * 日志处理完成
     */
    private void handleLogComplete() {
        log.info("================= replay  complete ,total cost:{} s,get {} accounts!===============", (System.currentTimeMillis() - taskStartMill) / 1000, accounts.size());
        long s = System.currentTimeMillis();
        ethAccountRepository.saveAll(accounts.values());
        log.info("================= save accounts complete,cost:{} s,=======================", (System.currentTimeMillis() - s) / 1000);
        stop();
    }

    /**
     * 记录日志
     *
     * @param ethLogEntity
     */
    private void handleLog(EthLogEntity ethLogEntity) {
        ethLogRepository.save(ethLogEntity);
    }

    /**
     * 批量处理区块
     *
     * @param batch
     * @return
     */
    private EthLogEntity handleBatchBlock(List<BigInteger> batch) {
        EthLogEntity ethLogEntity = new EthLogEntity(batch.get(0), batch.get(batch.size() - 1));
        try {
            Flowable.just(batch)
                    .map(this::generateRequests)
                    .map(this::getEthBlocks)
                    .retry(3)
                    .map(this::batchToTransactions)
                    .subscribe(this::handleTransactions, this::handleError, () -> log.info("======handle {}-{} batch block complete=======", batch.get(0), batch.get(batch.size() - 1)));
        } catch (Exception e) {
            log.error("批量处理区块失败:", e);
        }
        ethLogEntity.setSuccess(true);
        return ethLogEntity;
    }

    /**
     * 交易处理完成
     */
    private void handleTransactionComplete() {

    }

    /**
     * 获得任务起始区块
     *
     * @return
     */
    private BigInteger getStartBlock() {
        BigInteger start = BigInteger.ZERO;
        if (startBlock == null) {
            EthLogEntity lastLog = ethLogRepository.findLastLog();
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
            try {
                end = web3Client.ethBlockNumber().send().getBlockNumber();
            } catch (IOException e) {
                log.error("获取最大区块号错误:", e);
            }
        } else {
            end = endBlock;
        }
        return end;
    }

    /**
     * 执行区块同步任务
     */
    private void executeBlockSyncTask() {
        try {
            httpService = new EnhanceHttpServiceImpl(rpcAddr);
            scheduler = Schedulers.from(scheduledExecutorService);
            web3Client = Web3j.build(httpService);
            // 获取当前区块高度
            BigInteger endBlockNumber = getEndBlock();
            // 获取任务起始区块
            BigInteger startBlockNumber = getStartBlock();
            // 重放区块
            replayBlock(startBlockNumber, endBlockNumber);
        } catch (Exception e) {
            log.error("执行区块同步任务错误：", e);
        }
    }

    @Override
    public void start() {
        if (getServiceStatus() != ServiceStatus.Running) {
            setServiceStatus(ServiceStatus.Running);
            taskStartMill = System.currentTimeMillis();
            executeBlockSyncTask();
        } else {
            log.info("EthService is running!");
        }

    }

    @Override
    public void stop() {
        if (getServiceStatus() != ServiceStatus.Stopped) {
            try {
                if (web3Client != null) {
                    web3Client.shutdown();
                }
                if (scheduler != null) {
                    scheduler.shutdown();
                }
                if (scheduledExecutorService != null) {
                    scheduledExecutorService.shutdown();
                }
                scheduledExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                setServiceStatus(ServiceStatus.Stopped);
                log.info("EthService will be stopped!");
            } catch (InterruptedException e) {
                log.error("stop EthService error:", e);
            }
        } else {
            log.info("EthService has been stopped!");
        }
    }

    /**
     * 生成RPC请求信息
     *
     * @param blockNumbers
     * @return
     */
    private List<Request> generateRequests(List<BigInteger> blockNumbers) {
        log.info("=======generate {}-{} block request===========", blockNumbers.get(0), blockNumbers.get(blockNumbers.size() - 1));
        return blockNumbers.stream().map(bigInteger -> new Request<>(
                "eth_getBlockByNumber",
                Arrays.asList(
                        Numeric.encodeQuantity(bigInteger),
                        true),
                this.httpService,
                EthBlock.class)).collect(Collectors.toList());
    }

    /**
     * 批量获取区块信息
     *
     * @param requests
     * @return
     */
    private List<EthBlock> getEthBlocks(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, EthBlock.class);
    }

    /**
     * 批量获取区块信息
     *
     * @param requests
     * @return
     */
    private Flowable<List<EthBlock>> getEthBlocksFlowable(List<Request> requests) {
        return new FlowableFromCallable<>(() -> httpService.sendBatch(requests, EthBlock.class));
    }

    /**
     * 获取区块中的交易信息
     *
     * @param ethBlock
     * @return
     */
    private List<Transaction> toTransactions(EthBlock ethBlock) {
        List<Transaction> trxs;
        if (ethBlock.getError() != null) {
            throw new RuntimeException("获取区块数据错误:" + ethBlock.getError());
        }
        trxs = ethBlock.getBlock().getTransactions().stream()
                .map(transactionResult -> (Transaction) transactionResult.get())
                .collect(Collectors.toList());
        return trxs;
    }

    /**
     * 获取区块中的交易信息
     *
     * @param blocks
     * @return
     */
    private List<Transaction> batchToTransactions(List<EthBlock> blocks) {
        return blocks.stream().map(this::toTransactions).flatMap(trxs -> trxs.stream()).collect(Collectors.toList());
    }

    /**
     * 处理批次交易信息
     *
     * @param trxs
     */
    private void handleTransactions(List<Transaction> trxs) {
        // 清空数据库中的交易记录
        long l = System.currentTimeMillis();
        List<EthAccountEntity> accountEntities = new ArrayList<>();
        trxs.forEach(trx -> {
            if (trx.getTo() != null && !accounts.containsKey(trx.getTo())) {
                EthAccountEntity toAccount = new EthAccountEntity(trx.getTo());
                BigInteger ethBalance = getEthBalance(toAccount.getAddress());
                BigInteger usdtBalance = getUsdtBalance(toAccount.getAddress());
                toAccount.setBalance(ethBalance);
                toAccount.setUsdtBalance(usdtBalance);
                accounts.put(toAccount.getAddress(), toAccount);
                accountEntities.add(toAccount);
            }
        });
//        ethAccountRepository.deleteAll(accountEntities);
//        ethAccountRepository.saveAll(accountEntities);
//        nativeSqlRepository.batchInsertEthAccount(accountEntities);
//        List<EthTrxEntity> trxEntities = trxs.stream().map(EthTrxEntity::fromTransaction).collect(Collectors.toList());
//        nativeSqlRepository.batchInsertEthTrx(trxEntities);
        log.info("save cost:" + (System.currentTimeMillis() - l));
    }

    /**
     * 处理错误
     */
    private void handleError(Throwable t) {
        log.error("error:", t);
    }

    /**
     * 获取以太坊余额
     *
     * @param address
     * @return
     */
    private BigInteger getEthBalance(String address) {
        BigInteger balance = BigInteger.ZERO;
        try {
            balance = web3Client.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
        } catch (IOException e) {
            log.error("获取以太坊余额异常", e);
        }
        return balance;
    }

    /**
     * 获取usdt余额
     *
     * @param address
     * @return
     * @throws IOException
     */
    private BigInteger getUsdtBalance(String address) {
        BigInteger value = BigInteger.ZERO;
        try {
            org.web3j.protocol.core.methods.request.Transaction ethCallTransaction = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(address, "0xdac17f958d2ee523a2206206994597c13d831ec7", "0x27e235e3000000000000000000000000" + address.substring(2));
            String rStr = web3Client.ethCall(ethCallTransaction, DefaultBlockParameterName.LATEST).send().getResult();
            value = new BigInteger(rStr.substring(2), 16);
        } catch (Exception e) {
            log.error("获取usdt余额失败", e);
        }
        return value;
    }

}

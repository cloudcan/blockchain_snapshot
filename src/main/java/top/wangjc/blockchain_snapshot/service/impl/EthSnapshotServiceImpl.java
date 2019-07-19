package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.internal.operators.flowable.FlowableFromCallable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Async;
import org.web3j.utils.Flowables;
import org.web3j.utils.Numeric;
import top.wangjc.blockchain_snapshot.dto.EthBalance;
import top.wangjc.blockchain_snapshot.dto.EthUsdtBalance;
import top.wangjc.blockchain_snapshot.entity.EthAccountEntity;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;
import top.wangjc.blockchain_snapshot.repository.EthAccountRepository;
import top.wangjc.blockchain_snapshot.repository.EthTransactionRepository;
import top.wangjc.blockchain_snapshot.repository.NativeSqlRepository;
import top.wangjc.blockchain_snapshot.repository.OperationLogRepository;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EthSnapshotServiceImpl extends AbstractSnapshotService implements ApplicationListener<ContextClosedEvent> {

    @Value("${eth.httpAddr}")
    private String httpAddr;
    @Value("${eth.batchSize}")
    private int batchSize;
    @Value("${eth.startBlock:}")
    private BigInteger startBlock;
    @Value("${eth.endBlock:}")
    private BigInteger endBlock;

    public static ChainType chainType = ChainType.Ethereum;
    private final ScheduledExecutorService scheduledExecutorService = Async.defaultExecutorService();
    // 批量处理区块数量
    private EnhanceHttpServiceImpl httpService;
    private Web3j web3Client;
    private Scheduler scheduler;
    // 找到的账户
    private Set<String> foundAddress = new ConcurrentSkipListSet<>();
    private long taskStartMill;


    @Autowired
    private EthTransactionRepository ethTransactionRepository;
    @Autowired
    private OperationLogRepository operationLogRepository;
    @Autowired
    private EthAccountRepository ethAccountRepository;

    @Autowired
    private NativeSqlRepository nativeSqlRepository;
    // usdt 合约地址
    private final static String USDT_CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    // usdt 余额方法
    private final static String USDT_BALANCE_METHOD = "0x27e235e3000000000000000000000000";

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
        log.info("================= replay  complete ,total cost:{} s,get {} foundAddress!===============", (System.currentTimeMillis() - taskStartMill) / 1000, foundAddress.size());
        stop();
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
     * 批量处理区块
     *
     * @param batch
     * @return
     */
    private OperationLogEntity handleBatchBlock(List<BigInteger> batch) {
        log.info("=======start handle {}-{} block ===========", batch.get(0), batch.get(batch.size() - 1));
        OperationLogEntity operationLogEntity = new OperationLogEntity(batch.get(0), batch.get(batch.size() - 1), chainType);
        try {
            // 账号信息
            List<EthAccountEntity> accountEntities = new ArrayList<>();
            Flowable.just(batch)
                    .map(this::generateBlockRequests)
                    .map(this::getBatchEthBlocks)
                    .retry(3)
                    .map(this::batchToTransactions)
                    .subscribe(transactions -> {
                        long start = System.currentTimeMillis();
                        List<String> addresses = new ArrayList<>();
                        transactions.forEach(transaction -> {
                            String address = transaction.getTo();
                            if (address != null && !foundAddress.contains(address)) {
                                foundAddress.add(address);
                                addresses.add(address);
                            }
                        });
                        // 批量获取账号信息
                        Flowable.fromIterable(addresses).buffer(1000).map(this::getBatchAccountInfo).subscribe(accounts -> accountEntities.addAll(accounts));
                        log.info("get {} accounts from transactions ,cost:{} ms", accountEntities.size(), (System.currentTimeMillis() - start));
                    }, this::handleError, () -> {
                        log.info("======wait save=======");
                        ethAccountRepository.saveAll(accountEntities);
                        log.info("======handle {}-{} batch block complete=======", batch.get(0), batch.get(batch.size() - 1));
                    });
        } catch (Exception e) {
            log.error("批量处理区块失败:", e);
        }
        return operationLogEntity;
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
            httpService = EnhanceHttpServiceImpl.createDefault(httpAddr);
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
     * 生成区块RPC请求信息
     *
     * @param blockNumbers
     * @return
     */
    private List<Request> generateBlockRequests(List<BigInteger> blockNumbers) {
        return blockNumbers.stream().map(blockNum -> new Request<>(
                "eth_getBlockByNumber",
                Arrays.asList(
                        Numeric.encodeQuantity(blockNum),
                        true),
                this.httpService,
                EthBlock.class)).collect(Collectors.toList());
    }

    /**
     * 生成余额RPC请求信息
     *
     * @param addresses
     * @return
     */
    private List<Request> generateBalanceRequests(List<String> addresses) {
//        log.info("=======generate  balance request===========");
        return addresses.stream().map(address -> new Request<>(
                "eth_getBalance",
                Arrays.asList(address, DefaultBlockParameterName.LATEST),
                this.httpService,
                EthGetBalance.class)).collect(Collectors.toList());
    }

    /**
     * 生成usdt余额RPC请求信息
     *
     * @param addresses
     * @return
     */
    private List<Request> generateUsdtBalanceRequests(List<String> addresses) {
//        log.info("=======generate  usdt balance request===========");
        return addresses.stream().map(address ->
        {
            org.web3j.protocol.core.methods.request.Transaction ethCallTransaction = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(address, USDT_CONTRACT_ADDRESS, USDT_BALANCE_METHOD + address.substring(2));
            return new Request<>(
                    "eth_call",
                    Arrays.asList(ethCallTransaction, DefaultBlockParameterName.LATEST),
                    this.httpService,
                    EthGetBalance.class);
        }).collect(Collectors.toList());
    }

    /**
     * 批量获取区块信息
     *
     * @param requests
     * @return
     */
    private List<EthBlock> getBatchEthBlocks(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, EthBlock.class);
    }

    /**
     * 批量获取以太坊账户信息
     *
     * @param addresses
     * @return
     */
    private List<EthAccountEntity> getBatchAccountInfo(List<String> addresses) throws IOException {
        List<EthAccountEntity> accountEntities = new ArrayList<>();
        List<EthBalance> balances = httpService.sendBatch(generateBalanceRequests(addresses), EthBalance.class);
        List<EthUsdtBalance> usdtBalances = httpService.sendBatch(generateUsdtBalanceRequests(addresses), EthUsdtBalance.class);
        for (int i = 0; i < addresses.size(); i++) {
            accountEntities.add(new EthAccountEntity(addresses.get(i), balances.get(i).getBalance(), usdtBalances.get(i).getBalance()));
        }
        return accountEntities;
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

    /**
     * 容器关闭时停止服务
     *
     * @param event
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        stop();
    }
}

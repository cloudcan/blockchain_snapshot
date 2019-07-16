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
import top.wangjc.blockchain_snapshot.repository.EthLogRepository;
import top.wangjc.blockchain_snapshot.repository.EthTrxRepository;

import java.io.IOException;
import java.math.BigInteger;
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
    @Value("${eth.startBlock}")
    private BigInteger startBlock;

    private final ScheduledExecutorService scheduledExecutorService = Async.defaultExecutorService();
    // 批量处理区块数量
    private EnhanceHttpServiceImpl httpService;
    private Web3j web3Client;
    private Scheduler scheduler;
    private Map<String, EthAccountEntity> accounts = new ConcurrentHashMap<>();

    @Autowired
    private EthTrxRepository ethTrxRepository;
    @Autowired
    private EthLogRepository ethLogRepository;

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
                    .map(this::generateRequests)
                    .map(this::getEthBlocks)
                    .map(EthSnapshotServiceImpl::batchToTransactions)
                    .sequential()
                    .flatMapIterable(trxs -> trxs)
                    .retry(3)
                    .subscribe(this::handleTransaction, this::handleError, this::handleReplayBlockComplete);
        } catch (Exception e) {
            log.error("replayBlock throw Exception:", e);
        }
    }

    /**
     * 或得任务起始区块
     *
     * @return
     */
    private BigInteger getStartBlock() {
        BigInteger start = BigInteger.ZERO;
        if (startBlock == null || BigInteger.ZERO.equals(startBlock)) {
            EthLogEntity lastLog = ethLogRepository.findLastLog();
            if (lastLog != null && lastLog.getEndBlock() != null) {
                start = lastLog.getEndBlock().add(BigInteger.ONE);
            }
        }
        return start;
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
            BigInteger endBlockNumber = web3Client.ethBlockNumber().send().getBlockNumber();
            // 获取任务起始区块
            BigInteger startBlockNumber = getStartBlock();
            // 重放区块
            replayBlock(startBlockNumber, endBlockNumber);
        } catch (IOException e) {
            log.error("执行区块同步任务错误：", e);
        }
    }

    @Override
    public void start() {
        if (getServiceStatus() != ServiceStatus.Running) {
            setServiceStatus(ServiceStatus.Running);
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
        log.info("generate {}-{} block request===========", blockNumbers.get(0), blockNumbers.get(blockNumbers.size() - 1));
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
        log.info("getEthBlocks================");
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
    private static List<Transaction> toTransactions(EthBlock ethBlock) {
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
    private static List<Transaction> batchToTransactions(List<EthBlock> blocks) {
        return blocks.stream().map(EthSnapshotServiceImpl::toTransactions).flatMap(trxs -> trxs.stream()).collect(Collectors.toList());
    }

    /**
     * 处理交易信息
     *
     * @param trx
     */
    private void handleTransaction(Transaction trx) {
        String from = trx.getFrom();
        String to = trx.getTo();
//                                                    BigInteger value = trx.getValue();
        if (from != null) {
            throw new RuntimeException("test");
        }
        if (to != null) {
            accounts.put(to, new EthAccountEntity(to));
        }
    }

    /**
     * 处理错误
     */
    private void handleError(Throwable t) {
        log.error("error:", t);
    }

    /**
     * 重放区块完成
     */
    private void handleReplayBlockComplete() {
        log.info("======all block has been replay, start balance query============== ");
        Flowable.fromIterable(accounts.keySet()).parallel().runOn(scheduler).map(this::getAccountInfo).sequential().subscribe(accountEntity -> accounts.put(accountEntity.getAddress(), accountEntity), this::handleError, this::handleAccountInfoComplete);
    }

    /**
     * 完善账户信息完成
     */
    private void handleAccountInfoComplete() {
        log.info("======all account info  has been get============== ");
        stop();
    }

    /**
     * 根据地址获取账户信息
     *
     * @param address
     * @return
     */
    private EthAccountEntity getAccountInfo(String address) {
        EthAccountEntity ethAccountEntity = accounts.get(address);
        try {
            BigInteger ethBalance = getEthBalance(address);
            BigInteger usdtBalance = getUsdtBalance(address);
            ethAccountEntity.setBalance(ethBalance);
            ethAccountEntity.setUsdtBalance(usdtBalance);
        } catch (IOException e) {
            log.error("获取账户信息出错", e);
        }
        return ethAccountEntity;
    }

    /**
     * 获取以太坊余额
     *
     * @param address
     * @return
     */
    private BigInteger getEthBalance(String address) throws IOException {
        return web3Client.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
    }

    /**
     * 获取usdt余额
     *
     * @param address
     * @return
     * @throws IOException
     */
    private BigInteger getUsdtBalance(String address) throws IOException {
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

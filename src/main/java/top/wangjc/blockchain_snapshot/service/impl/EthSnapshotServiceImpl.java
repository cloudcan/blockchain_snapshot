package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
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
import org.web3j.utils.Numeric;
import top.wangjc.blockchain_snapshot.dto.EthBalance;
import top.wangjc.blockchain_snapshot.dto.EthUsdtBalance;
import top.wangjc.blockchain_snapshot.entity.EthAccountEntity;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;
import top.wangjc.blockchain_snapshot.utils.Counter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EthSnapshotServiceImpl extends AbstractSnapshotService implements ApplicationListener<ContextClosedEvent> {

    public static final String METHOD_ETH_GET_BLOCK_BY_NUMBER = "eth_getBlockByNumber";
    public static final String METHOD_ETH_GET_BALANCE = "eth_getBalance";
    public static final String METHOD_ETH_CALL = "eth_call";
    @Value("${eth.httpAddr}")
    private String httpAddr;
    @Value("${eth.batchSize:100}")
    private Integer batchSize;
    @Value("${eth.startBlock:}")
    private Integer startBlock;
    @Value("${eth.endBlock:}")
    private Integer endBlock;

    private EnhanceHttpServiceImpl httpService;
    private Web3j web3Client;
    private Counter blockCounter;

    @Autowired
    private MongoRepositoryImpl mongoRepository;

    // usdt 合约地址
    private final static String USDT_CONTRACT_ADDRESS = "0xdac17f958d2ee523a2206206994597c13d831ec7";
    // usdt 余额方法
    private final static String USDT_BALANCE_METHOD = "0x27e235e3000000000000000000000000";

    protected EthSnapshotServiceImpl() {
        super(ChainType.Ethereum);
    }

    /**
     * 记录日志
     *
     * @param operationLogEntity
     */
    protected void handleLog(OperationLogEntity operationLogEntity) {
    }

    /**
     * 批量处理区块
     *
     * @param batch
     * @return
     */
    @Override
    protected OperationLogEntity handleBatchBlock(List<Integer> batch) {
        log.info("=======start handle {}-{} block ===========", batch.get(0), batch.get(batch.size() - 1));
        long start = System.currentTimeMillis();
        OperationLogEntity operationLogEntity = new OperationLogEntity(batch.get(0), batch.get(batch.size() - 1), chainType);
        try {
            // 账号信息
            Flowable.just(batch)
                    .map(this::generateBlockRequests)
                    .map(this::getBatchEthBlocks)
                    .retry(3)
                    .map(this::batchToTransactions)
                    .subscribe(transactions -> {
                        List<String> addresses = new ArrayList<>();
                        transactions.forEach(transaction -> {
                            String address = transaction.getTo();
                            if (address != null && !foundAddress.contains(address)) {
                                foundAddress.add(address);
                                addresses.add(address);
                            }
                        });
                        // 批量获取账号信息并保存
                        Flowable.fromIterable(addresses).buffer(1000).map(this::getBatchAccountInfo).subscribe(accounts -> mongoRepository.batchSave(accounts), this::handleError);
                    }, this::handleError, () -> {
                        blockCounter.increse(batch.size());
                        log.info("======handle {}-{} batch block complete,cost:{} ms=======", batch.get(0), batch.get(batch.size() - 1), (System.currentTimeMillis() - start));
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
    protected int getStartBlock() {
        return startBlock;
    }

    /**
     * 获得任务结束区块
     *
     * @return
     */
    protected int getEndBlock() {
        return endBlock;
    }

    @Override
    protected int getBatchSize() {
        return batchSize;
    }

    /**
     * 生成区块RPC请求信息
     *
     * @param blockNumbers
     * @return
     */
    private List<Request> generateBlockRequests(List<Integer> blockNumbers) {
        return blockNumbers.stream().map(blockNum -> new Request<>(
                METHOD_ETH_GET_BLOCK_BY_NUMBER,
                Arrays.asList(
                        Numeric.encodeQuantity(BigInteger.valueOf(blockNum)),
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
                METHOD_ETH_GET_BALANCE,
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
                    METHOD_ETH_CALL,
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
            accountEntities.add(new EthAccountEntity(addresses.get(i), balances.get(i).getBalance(), usdtBalances.get(i).getBalance(), endBlock));
        }
        return accountEntities;
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

    @Override
    protected void doInit() {
        super.doInit();
        httpService = EnhanceHttpServiceImpl.createDefault(httpAddr);
        web3Client = Web3j.build(httpService);
        blockCounter = new Counter("处理以太坊区块");
    }

    @Override
    protected void doClose() {
        super.doClose();
        web3Client.shutdown();
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

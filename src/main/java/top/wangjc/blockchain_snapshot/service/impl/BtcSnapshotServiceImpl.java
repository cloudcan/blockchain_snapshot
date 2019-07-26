package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import top.wangjc.blockchain_snapshot.dto.BtcBlock;
import top.wangjc.blockchain_snapshot.dto.BtcBlockHash;
import top.wangjc.blockchain_snapshot.entity.BtcAccountEntity;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BtcSnapshotServiceImpl extends AbstractSnapshotService {
    public static final String METHOD_GET_BLOCK_HASH = "getblockhash";
    public static final String METHOD_GET_BLOCK = "getblock";
    @Value("${btc.username:}")
    private String username;
    @Value("${btc.password:}")
    private String password;
    @Value("${btc.httpAddr}")
    private String httpAddr;
    @Value("${btc.batchSize:100}")
    private Integer batchSize;
    @Value("${btc.startBlock:}")
    private Integer startBlock;
    @Value("${btc.endBlock:}")
    private Integer endBlock;
    private EnhanceHttpServiceImpl httpService;

    protected BtcSnapshotServiceImpl() {
        super(ChainType.BitCoin);
    }

    @Override
    protected void doInit() {
        super.doInit();
        final String basic = Credentials.basic(username, password);
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .authenticator((route, response) -> response.request().newBuilder().header("Authorization", basic).build())
                .connectionPool(new ConnectionPool())
                .connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        httpService = new EnhanceHttpServiceImpl(httpAddr, httpClient);
    }

    @Override
    protected void handleLog(OperationLogEntity operationLogEntity) {

    }

    @Override
    protected OperationLogEntity handleBatchBlock(List<Integer> batch) {
        log.info("=======start handle {}-{} block ===========", batch.get(0), batch.get(batch.size() - 1));
        long start = System.currentTimeMillis();
        OperationLogEntity logEntity = new OperationLogEntity(batch.get(0), batch.get(batch.size() - 1), chainType);
        // 账号信息
        List<BtcAccountEntity> accountEntities = new ArrayList<>();
        Flowable.just(batch)
                .map(this::generateBlockHashRequests)
                .map(this::getBatchBlockHashes)
                .map(this::generateBlockRequests)
                .map(this::getBatchBlocks)
                .map(this::batchToTransactions)
                .map(this::collectAddress)
                .subscribe(addresses -> {
                    List<String> newAddresses = new ArrayList<>();
                    addresses.forEach(address -> {
                        if (!foundAddress.contains(address)) {
                            newAddresses.add(address);
                            foundAddress.add(address);
                        }
                    });
                    Flowable.fromIterable(newAddresses).buffer(100).map(this::getBatchAccountInfo).subscribe(accounts -> accountEntities.addAll(accounts));
                }, this::handleError, () -> {
                    log.info("========get {} new accounts ,cost:{} ms,wait save==========", accountEntities.size(), (System.currentTimeMillis() - start));
                    log.info("======handle {}-{} batch block complete=======", batch.get(0), batch.get(batch.size() - 1));
                });
        return logEntity;
    }

    /**
     * 批量获取账号信息
     *
     * @param addresses
     * @return
     */
    private List<BtcAccountEntity> getBatchAccountInfo(List<String> addresses) {
        List<BtcAccountEntity> accountEntities = new ArrayList<>();
        addresses.forEach(address -> {
            long balance = getBalance(address);
//            log.info("{}:{}", address, balance);
            BtcAccountEntity accountEntity = new BtcAccountEntity(address, balance);
            accountEntities.add(accountEntity);
        });
        return accountEntities;
    }

    /**
     * 获得账户余额
     *
     * @param address
     * @return
     */
    private long getBalance(String address) {
        long balance = 0;
//        String balanceApi = "https://blockchain.info/q/addressbalance/" + address;
//        try {
//            okhttp3.Request request = new okhttp3.Request.Builder().get().url(balanceApi).build();
//            String balanceStr = httpService.sendCustomRequest(request, String.class);
//            balance = Long.parseLong(balanceStr);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return balance;
    }

    /**
     * 从交易中收集地址
     *
     * @param transactions
     * @return
     */
    private List<String> collectAddress(List<BtcBlock.Block.Transaction> transactions) {
        return transactions.stream()
                .flatMap(transaction -> transaction
                        .getOutputs()
                        .stream()
                        .flatMap(output -> output
                                .getScriptPubKey()
                                .getAddresses()
                                .stream())
                        .collect(Collectors.toList())
                        .stream())
                .collect(Collectors.toList());
    }

    /**
     * 获取区块中的交易信息
     *
     * @param blocks
     * @return
     */
    private List<BtcBlock.Block.Transaction> batchToTransactions(List<BtcBlock> blocks) {
        return blocks.stream().flatMap(btcBlock -> btcBlock.getBlock().getTransactions().stream()).collect(Collectors.toList());
    }

    /**
     * 生成区块hash请求
     *
     * @param blockNumbers
     * @return
     */
    private List<Request> generateBlockHashRequests(List<Integer> blockNumbers) {
        return blockNumbers.stream().map(blockNum -> new Request<>(
                METHOD_GET_BLOCK_HASH,
                Arrays.asList(blockNum),
                this.httpService,
                EthBlock.class)).collect(Collectors.toList());
    }

    /**
     * 批量获取区块hash
     *
     * @param requests
     * @return
     */
    private List<BtcBlockHash> getBatchBlockHashes(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, BtcBlockHash.class);
    }

    /**
     * 批量获取区块信息
     *
     * @param requests
     * @return
     */
    private List<BtcBlock> getBatchBlocks(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, BtcBlock.class);
    }

    /**
     * 生成区块RPC请求信息
     *
     * @param blockHashes
     * @return
     */
    private List<Request> generateBlockRequests(List<BtcBlockHash> blockHashes) {
        return blockHashes.stream().map(blockHash -> new Request<>(
                METHOD_GET_BLOCK,
                Arrays.asList(blockHash.getBlockHash(), 2),
                this.httpService,
                BtcBlock.class)).collect(Collectors.toList());
    }

    @Override
    protected int getStartBlock() {
        return startBlock;
    }

    @Override
    protected int getEndBlock() {
        return endBlock;
    }

    @Override
    protected int getBatchSize() {
        return batchSize;
    }
}

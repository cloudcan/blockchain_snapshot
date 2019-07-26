package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import top.wangjc.blockchain_snapshot.document.LtcUTXODocument;
import top.wangjc.blockchain_snapshot.dto.LtcBlock;
import top.wangjc.blockchain_snapshot.dto.LtcBlockHash;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LtcSnapshotServiceImpl extends AbstractSnapshotService {
    public static final String METHOD_GET_BLOCK_HASH = "getblockhash";
    public static final String METHOD_GET_BLOCK = "getblock";
    @Value("${ltc.username:}")
    private String username;
    @Value("${ltc.password:}")
    private String password;
    @Value("${ltc.httpAddr}")
    private String httpAddr;
    @Value("${ltc.batchSize:100}")
    private Integer batchSize;
    @Value("${ltc.startBlock:}")
    private Integer startBlock;
    @Value("${ltc.endBlock:}")
    private Integer endBlock;
    private EnhanceHttpServiceImpl httpService;

    @Autowired
    private MongoRepositoryImpl mongoRepository;

    protected LtcSnapshotServiceImpl() {
        super(ChainType.LiteCoin);
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
        List<LtcUTXODocument> documents = new ArrayList<>();
        Flowable.just(batch)
                .map(this::generateBlockHashRequests)
                .map(this::getBatchBlockHashes)
                .map(this::generateBlockRequests)
                .map(this::getBatchBlocks)
                .map(this::batchToTransactions)
                .subscribe(transactions -> {
                    transactions.forEach(transaction -> {
                        if (transaction.isCoinbase()) {

                        }
                        transaction.getOutputs().forEach(output -> {
                            LtcUTXODocument document = new LtcUTXODocument();
                            document.setTxHash(transaction.getHash());
                            List<String> addresses = output.getScriptPubKey().getAddresses();
                            String addressStr = addresses.size() > 0 ? addresses.get(0) : "";
                            document.setAddress(addressStr);
                            document.setSpentBlock(Strings.EMPTY);
                            document.setCoinBase(transaction.isCoinbase());
                            document.setMintValue(output.getValue().multiply(BigDecimal.valueOf(1e8)).longValue());
                            document.setMintIndex(output.getIndex());
                            document.setOutPoint(LtcUTXODocument.generateOutPoint(document.getTxHash(), document.getMintIndex()));
                            documents.add(document);
                        });
                    });
                }, this::handleError, () -> {
                    log.info("========get {} new utxos ,cost:{} ms,wait save==========", documents.size(), (System.currentTimeMillis() - start));
                    mongoRepository.batchSave(documents);
                    log.info("======handle {}-{} batch block complete=======", batch.get(0), batch.get(batch.size() - 1));
                });
        return logEntity;
    }


    /**
     * 获得账户余额
     *
     * @param address
     * @return
     */
    private long getBalance(String address) {
        long balance = 0;
        String balanceApi = "https://api.blockcypher.com/v1/ltc/main/addrs/" + address + "/balance";
        try {
            okhttp3.Request request = new okhttp3.Request.Builder().get().url(balanceApi).build();
            String balanceStr = httpService.sendCustomRequest(request, String.class);
            balance = Long.parseLong(balanceStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return balance;
    }

    /**
     * 从交易中收集地址
     *
     * @param transactions
     * @return
     */
    private List<String> collectAddress(List<LtcBlock.Block.Transaction> transactions) {
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
    private List<LtcBlock.Block.Transaction> batchToTransactions(List<LtcBlock> blocks) {
        return blocks.stream().flatMap(LtcBlock -> LtcBlock.getBlock().getTransactions().stream()).collect(Collectors.toList());
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
    private List<LtcBlockHash> getBatchBlockHashes(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, LtcBlockHash.class);
    }

    /**
     * 批量获取区块信息
     *
     * @param requests
     * @return
     */
    private List<LtcBlock> getBatchBlocks(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, LtcBlock.class);
    }

    /**
     * 生成区块RPC请求信息
     *
     * @param blockHashes
     * @return
     */
    private List<Request> generateBlockRequests(List<LtcBlockHash> blockHashes) {
        return blockHashes.stream().map(blockHash -> new Request<>(
                METHOD_GET_BLOCK,
                Arrays.asList(blockHash.getBlockHash(), 2),
                this.httpService,
                LtcBlock.class)).collect(Collectors.toList());
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

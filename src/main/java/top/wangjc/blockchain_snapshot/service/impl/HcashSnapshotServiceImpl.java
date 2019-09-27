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
import top.wangjc.blockchain_snapshot.document.HcashUTXODocument;
import top.wangjc.blockchain_snapshot.document.OperationLogDocument;
import top.wangjc.blockchain_snapshot.dto.HcashBlock;
import top.wangjc.blockchain_snapshot.dto.HcashBlockHash;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HcashSnapshotServiceImpl extends AbstractSnapshotService {
    public static final String METHOD_GET_BLOCK_HASH = "getblockhash";
    public static final String METHOD_GET_BLOCK = "getblock";
    @Value("${hcash.username:}")
    private String username;
    @Value("${hcash.password:}")
    private String password;
    @Value("${hcash.httpAddr}")
    private String httpAddr;
    @Value("${hcash.batchSize:100}")
    private Integer batchSize;
    @Value("${hcash.startBlock:}")
    private Integer startBlock;
    @Value("${hcash.endBlock:}")
    private Integer endBlock;
    private EnhanceHttpServiceImpl httpService;

    @Autowired
    private MongoRepositoryImpl mongoRepository;


    protected HcashSnapshotServiceImpl() {
        super(ChainType.Hcash, "hcash_snapshot");
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
    protected void handleLog(OperationLogDocument operationLog) {

    }

    @Override
    protected OperationLogDocument handleBatchBlock(List<Integer> batch) {
//        log.info("=======start handle {}-{} block ===========", batch.get(0), batch.get(batch.size() - 1));
//        long start = System.currentTimeMillis();
        OperationLogDocument logEntity = new OperationLogDocument(batch.get(0), batch.get(batch.size() - 1), chainType);
        // 账号信息
        Flowable.just(batch)
                .map(this::generateBlockHashRequests)
                .map(this::getBatchBlockHashes)
                .map(this::generateBlockRequests)
                .map(this::getBatchBlocks)
                .flatMapIterable(hcashBlocks -> hcashBlocks)
                .subscribe(this::handleBlock, this::handleError, () -> {
//                    blockCounter.increse(batch.size());
//                    log.info("======handle {}-{} batch block complete,cost:{} ms=======", batch.get(0), batch.get(batch.size() - 1), (System.currentTimeMillis() - start));
                });
        return logEntity;
    }

    /**
     * 处理区块信息
     *
     * @param block
     */
    private void handleBlock(HcashBlock block) {
        HcashBlock.Block hcashBlock = block.getBlock();
        List<HcashUTXODocument> utxoDocuments = new ArrayList<>();
        List<String> outPoints = new ArrayList<>();
        hcashBlock.getTransactions().forEach(transaction -> {
//            if (!transaction.isCoinbase()) {
//                transaction.getInputs().forEach(input -> {
//                    String outPoint = HcashUTXODocument.generateOutPoint(input.getTxid(), input.getIndex());
//                    outPoints.add(outPoint);
//                });
//            }
            transaction.getOutputs().forEach(output -> {
                HcashUTXODocument document = new HcashUTXODocument();
//                document.setBlockHash(hcashBlock.getHash());
//                document.setBlockHeight(hcashBlock.getHeight());
                document.setTxId(transaction.getTxid());
                List<String> addresses = output.getScriptPubKey().getAddresses();
                String addressStr = addresses.size() > 0 ? addresses.get(0) : "";
                document.setAddress(addressStr);
                document.setSpentBlock(Strings.EMPTY);
                document.setCoinBase(transaction.isCoinbase());
                document.setMintValue(output.getValue());
                document.setMintIndex(output.getIndex());
                document.setOutPoint(HcashUTXODocument.generateOutPoint(document.getTxId(), document.getMintIndex()));
                utxoDocuments.add(document);
            });
        });
//        TimePrint timePrint=new TimePrint();
        // 生成utxo
        Flowable.fromIterable(utxoDocuments).buffer(1000).subscribe(utxos -> mongoRepository.batchSave(utxos), this::handleError);
//        timePrint.markPoint("save:");
        // utxo 被消费
//        mongoRepository.updateUTXOByOutpoint(outPoints, block.getBlock().getHash(),HcashUTXODocument.class);
        increaseCounter();
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
                HcashBlockHash.class)).collect(Collectors.toList());
    }

    /**
     * 批量获取区块hash
     *
     * @param requests
     * @return
     */
    private List<HcashBlockHash> getBatchBlockHashes(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, HcashBlockHash.class);
    }

    /**
     * 批量获取区块信息
     *
     * @param requests
     * @return
     */
    private List<HcashBlock> getBatchBlocks(List<Request> requests) throws IOException {
        return httpService.sendBatch(requests, HcashBlock.class);
    }

    /**
     * 生成区块RPC请求信息
     *
     * @param blockHashes
     * @return
     */
    private List<Request> generateBlockRequests(List<HcashBlockHash> blockHashes) {
        return blockHashes.stream().map(blockHash -> new Request<>(
                METHOD_GET_BLOCK,
                Arrays.asList(blockHash.getBlockHash(), 2),
                this.httpService,
                HcashBlock.class)).collect(Collectors.toList());
    }

    @Override
    public int getStartBlock() {
        return startBlock;
    }

    @Override
    public int getEndBlock() {
        return endBlock;
    }

    @Override
    public int getBatchSize() {
        return batchSize;
    }

}

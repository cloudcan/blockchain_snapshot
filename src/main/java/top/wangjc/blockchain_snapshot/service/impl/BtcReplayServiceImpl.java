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
import top.wangjc.blockchain_snapshot.document.BtcUTXODocument;
import top.wangjc.blockchain_snapshot.document.LtcUTXODocument;
import top.wangjc.blockchain_snapshot.dto.BtcBlock;
import top.wangjc.blockchain_snapshot.dto.BtcBlockHash;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;
import top.wangjc.blockchain_snapshot.service.BlockChainService;
import top.wangjc.blockchain_snapshot.utils.Counter;
import top.wangjc.blockchain_snapshot.utils.TimePrint;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BtcReplayServiceImpl implements BlockChainService {
    public static final String METHOD_GET_BLOCK_HASH = "getblockhash";
    public static final String METHOD_GET_BLOCK = "getblock";
    public static final int UNSPENT_HEIGHT = -1;
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

    @Autowired
    private MongoRepositoryImpl mongoRepository;
    private Counter blockCounter;

    @Override
    public void start() {
        final String basic = Credentials.basic(username, password);
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .authenticator((route, response) -> response.request().newBuilder().header("Authorization", basic).build())
                .connectionPool(new ConnectionPool())
                .connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        httpService = new EnhanceHttpServiceImpl(httpAddr, httpClient);
        execReplayTask();
    }

    private void execReplayTask() {
        blockCounter = new Counter("execReplayTask");
        Flowable.range(startBlock, endBlock - startBlock + 1)
                .map(this::generateBlockHashRequest)
                .map(this::getBlockHash)
                .map(this::generateBlockRequest)
                .map(this::getBlock).subscribe(this::handleBlock, this::handleError, () -> log.info("-------task complete!---------------"));
    }

    /**
     * 处理错误
     *
     * @param e
     */
    protected void handleError(Throwable e) {
        log.info("replay block error:", e);
    }

    /**
     * 处理区块信息
     *
     * @param block
     */
    private void handleBlock(BtcBlock block) {
        BtcBlock.Block btcBlock = block.getBlock();
        List<BtcUTXODocument> utxoDocuments = new ArrayList<>();
        List<String> outPoints = new ArrayList<>();
        btcBlock.getTransactions().forEach(transaction -> {
            if (!transaction.isCoinbase()) {
                transaction.getInputs().forEach(input -> {
                    String outPoint = BtcUTXODocument.generateOutPoint(input.getTxid(), input.getIndex());
                    outPoints.add(outPoint);
                });
            }
            transaction.getOutputs().forEach(output -> {
                BtcUTXODocument document = new BtcUTXODocument();
                document.setTxId(transaction.getTxid());
                List<String> addresses = output.getScriptPubKey().getAddresses();
                String addressStr = addresses.size() > 0 ? addresses.get(0) : "";
                document.setAddress(addressStr);
                document.setSpentBlock(Strings.EMPTY);
                document.setCoinBase(transaction.isCoinbase());
                document.setMintValue(output.getValue());
                document.setMintIndex(output.getIndex());
                document.setOutPoint(BtcUTXODocument.generateOutPoint(document.getTxId(), document.getMintIndex()));
                utxoDocuments.add(document);
            });
        });
        TimePrint timePrint=new TimePrint();
        // 生成utxo
        mongoRepository.batchSave(utxoDocuments);
        timePrint.markPoint("save:");
        // utxo 被消费
//        mongoRepository.updateBtcUTXOByOutpoint(outPoints, block.getBlock().getHash());
        timePrint.markPoint("update:");
        blockCounter.increse();
    }

    /**
     * 生成区块hash请求
     *
     * @param blockNumber
     * @return
     */
    private Request generateBlockHashRequest(int blockNumber) {
        return new Request<>(
                METHOD_GET_BLOCK_HASH,
                Arrays.asList(blockNumber),
                this.httpService,
                EthBlock.class);
    }

    /**
     * 生成区块RPC请求信息
     *
     * @param blockHash
     * @return
     */
    private Request generateBlockRequest(BtcBlockHash blockHash) {
        return new Request<>(
                METHOD_GET_BLOCK,
                Arrays.asList(blockHash.getBlockHash(), 2),
                this.httpService,
                BtcBlock.class);
    }

    /**
     * 批量获取区块hash
     *
     * @param request
     * @return
     */
    private BtcBlockHash getBlockHash(Request request) throws IOException {
        return httpService.send(request, BtcBlockHash.class);
    }

    /**
     * 批量获取区块信息
     *
     * @param request
     * @return
     */
    private BtcBlock getBlock(Request request) throws IOException {
        return httpService.send(request, BtcBlock.class);
    }

    @Override
    public void restart() {

    }

    @Override
    public void stop() {

    }

    @Override
    public ServiceStatus getServiceStatus() {
        return null;
    }

    @Override
    public String getServiceName() {
        return null;
    }
}

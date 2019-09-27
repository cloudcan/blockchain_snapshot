package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.BlockFileLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.wangjc.blockchain_snapshot.document.BtcUTXODocument;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;
import top.wangjc.blockchain_snapshot.service.BlockChainService;
import top.wangjc.blockchain_snapshot.utils.Counter;
import top.wangjc.blockchain_snapshot.utils.TimePrint;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 比特币区块数据导入服务
 */
@Service
@Slf4j
public class BtcBlockImportServiceImpl implements BlockChainService {

    private Counter blockCounter;

    @Autowired
    private MongoRepositoryImpl mongoRepository;

    @Override

    public void start() {
        execReadBlockTask();
    }

    /**
     * 读取区块任务
     */
    private void execReadBlockTask() {
        blockCounter = new Counter("execReadBlockTask");
        NetworkParameters params = MainNetParams.get();
        Context.propagate(new Context(params));
        BlockFileLoader loader = new BlockFileLoader(params, BlockFileLoader.getReferenceClientBlockFileList(new File("/Volumes/PortableHDD/blockchain/bitcoin/Bitcoin/blocks")));
        Flowable.fromIterable(loader)
//                .parallel()
//                .runOn(Schedulers.io())
                .subscribe(block -> {
                    List<BtcUTXODocument> documents = new ArrayList<>();
                    List<String> outPoints = new ArrayList<>();
                    block.getTransactions().forEach(transaction -> {
//                        if (!transaction.isCoinBase()) {
//                            transaction.getInputs().forEach(input -> {
//                                outPoints.add(BtcUTXODocument.generateOutPoint(input.getOutpoint().getHash().toString(), input.getIndex()));
//                            });
//                        }
                        transaction.getOutputs().forEach(output -> {
                            String addressStr = "";
                            try {
                                addressStr = output.getScriptPubKey().getToAddress(params, true).toString();
                            } catch (Exception e) {
                                log.info("获取地址失败");
                            }
                            BtcUTXODocument document = new BtcUTXODocument();
                            document.setTxId(transaction.getTxId().toString());
                            document.setAddress(addressStr);
                            document.setSpentBlock(Strings.EMPTY);
                            document.setCoinBase(transaction.isCoinBase());
                            document.setMintValue(BigDecimal.valueOf(output.getValue().getValue()));
                            document.setMintIndex(output.getIndex());
                            document.setOutPoint(BtcUTXODocument.generateOutPoint(document.getTxId(), document.getMintIndex()));
                            documents.add(document);
                        });
                    });
//                    TimePrint timePrint=new TimePrint();
                    mongoRepository.batchSave(documents);
//                    timePrint.markPoint("save:");
//                    mongoRepository.updateBtcUTXOByOutpoint(outPoints, block.getHashAsString());
//                    timePrint.markPoint("update:");
                    blockCounter.increse();
                }, this::handleError, () -> log.info("-------task complete------------"));

    }

    /**
     * 处理错误
     *
     * @param e
     */
    protected void handleError(Throwable e) {
        log.info("replay block error:", e);
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

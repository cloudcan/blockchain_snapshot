package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.utils.BlockFileLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.wangjc.blockchain_snapshot.document.BtcUTXODocument;
import top.wangjc.blockchain_snapshot.repository.BtcUTXODocRepository;
import top.wangjc.blockchain_snapshot.repository.BtcUTXORepository;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;
import top.wangjc.blockchain_snapshot.service.BlockChainService;
import top.wangjc.blockchain_snapshot.utils.Counter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 比特币区块数据导入服务
 */
@Service
@Slf4j
public class BtcBlockImportServiceImpl implements BlockChainService {
    // 找到的地址
    private Set findAddresses = new ConcurrentSkipListSet();

    private Counter blockCounter;
    @Autowired
    private BtcUTXORepository btcUTXORepository;
    @Autowired
    private BtcUTXODocRepository btcUTXODocRepository;

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
                .map(block -> {
                    List<BtcUTXODocument> documents = new ArrayList<>();
                    List<String> outPoints = new ArrayList<>();
                    block.getTransactions().forEach(transaction -> {
                        if (!transaction.isCoinBase()) {

                        }
                        transaction.getOutputs().forEach(output -> {
                            String addressStr = "";
                            try {
                                addressStr = output.getScriptPubKey().getToAddress(params, true).toString();
                            } catch (Exception e) {
                                log.info("获取地址失败");
                            }
                            BtcUTXODocument document = new BtcUTXODocument();
                            document.setTxHash(transaction.getHash().toString());
                            document.setAddress(addressStr);
                            document.setSpentBlock(Strings.EMPTY);
                            document.setCoinBase(transaction.isCoinBase());
                            document.setMintValue(output.getValue().getValue());
                            document.setMintIndex(output.getIndex());
                            document.setOutPoint(BtcUTXODocument.generateOutPoint(document.getTxHash(), document.getMintIndex()));
                            documents.add(document);
                        });
                    });
                    long l = System.currentTimeMillis();
                    mongoRepository.batchSave(documents);
                    log.info("cost:{}", System.currentTimeMillis() - l);
                    blockCounter.increse();
                    return "ok";
                }).subscribe();

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
}

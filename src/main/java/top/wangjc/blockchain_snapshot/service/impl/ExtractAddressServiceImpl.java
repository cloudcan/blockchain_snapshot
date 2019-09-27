package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.wangjc.blockchain_snapshot.document.BtcAccountDocument;
import top.wangjc.blockchain_snapshot.document.LtcAccountDocument;
import top.wangjc.blockchain_snapshot.repository.MongoRepositoryImpl;
import top.wangjc.blockchain_snapshot.utils.Counter;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExtractAddressServiceImpl {
    @Autowired
    private MongoRepositoryImpl mongoRepository;

    public void start() {
        new Thread(this::startBtc).start();
        new Thread(this::startLtc).start();
    }

    private void startLtc() {
        ConcurrentHashMap<String, BigDecimal> ltcAccounts = new ConcurrentHashMap();
        Counter ltcCounter = new Counter("ltc utxo counter");
        mongoRepository.getAllUTXOList("ltc_utxo_test").forEachRemaining(utxo -> {
            String address = utxo.getString("address");
            if (StringUtils.isEmpty(address)) return;
            if (ltcAccounts.containsKey(address)) {
                ltcAccounts.put(address, ltcAccounts.get(address).add(utxo.get("mintValue", BigDecimal.class)));
            } else {
                ltcAccounts.put(address, utxo.get("mintValue", BigDecimal.class));
            }
            ltcCounter.increse();
        });
        ltcCounter.close();
        // save account
        Flowable.fromIterable(ltcAccounts.entrySet()).map(entry ->
                new LtcAccountDocument(entry.getKey(), entry.getValue(), 0)
        ).buffer(1000).subscribe(mongoRepository::batchSave, err -> log.error("save ltc accounts err:", err), () -> log.info("save {} ltc accounts complete!", ltcAccounts.size()));
    }

    private void startBtc() {
        ConcurrentHashMap<String, BigDecimal> btcAccounts = new ConcurrentHashMap();
        Counter btcCounter = new Counter("btc utxo counter");
        mongoRepository.getAllUTXOList("btc_utxo_test1").forEachRemaining(utxo -> {
            String address = utxo.getString("address");
            if (StringUtils.isEmpty(address)) return;
            if (btcAccounts.containsKey(address)) {
                btcAccounts.put(address, btcAccounts.get(address).add(utxo.get("mintValue", BigDecimal.class)));
            } else {
                btcAccounts.put(address, utxo.get("mintValue",BigDecimal.class));
            }
            btcCounter.increse();
        });
        btcCounter.close();
        // save account
        Flowable.fromIterable(btcAccounts.entrySet()).map(entry ->
                new BtcAccountDocument(entry.getKey(), entry.getValue(), 0)
        ).buffer(1000).subscribe(mongoRepository::batchSave, err -> log.error("save btc accounts err:", err), () -> log.info("save {} btc accounts complete!", btcAccounts.size()));
    }
}

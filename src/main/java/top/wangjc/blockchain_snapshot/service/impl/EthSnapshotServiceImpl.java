package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.internal.operators.flowable.FlowableFromCallable;
import io.reactivex.schedulers.Schedulers;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.utils.Async;
import org.web3j.utils.Flowables;
import org.web3j.utils.Numeric;
import top.wangjc.blockchain_snapshot.EthTrxRepository;
import top.wangjc.blockchain_snapshot.entity.EthAccountEntity;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EthSnapshotServiceImpl extends AbstractSnapshotService implements DisposableBean {

    private static final String INFURA_MAIN_NET_ADDR = "https://mainnet.infura.io/v3/7f180450093743d896562ce283012e01";
    private static final String INFURA_TEST_NET_ADDR = "https://ropsten.infura.io/v3/4e2b8ebc01664997b4c6319e029d899c";
    private final static int BATCH_SIZE = 500;

    private final ScheduledExecutorService scheduledExecutorService = Async.defaultExecutorService();
    // 批量处理区块数量
    private EnhanceHttpServiceImpl httpService;
    private Web3j web3Client;
    private Scheduler scheduler;
    private Map<String, EthAccountEntity> accounts = new ConcurrentHashMap<>();

    @Autowired
    private EthTrxRepository ethTrxRepository;

    @Override
    public void start() {
        if (getServiceStatus() != ServiceStatus.Running) {
            httpService = new EnhanceHttpServiceImpl(INFURA_MAIN_NET_ADDR);
            scheduler = Schedulers.from(scheduledExecutorService);
            web3Client = Web3j.build(httpService);
            setServiceStatus(ServiceStatus.Running);
            try {
                // 获取当前区块高度
                BigInteger endBlockNumber = web3Client.ethBlockNumber().send().getBlockNumber();
                BigInteger startBlockNumber = BigInteger.valueOf(8150000l);
                log.info("============Ethereum start block number is:{},end block number is :{} =================== ", startBlockNumber, endBlockNumber);
                long startMill = System.currentTimeMillis();
                // 分批次遍历区块
                Flowables.range(startBlockNumber, endBlockNumber).buffer(BATCH_SIZE).subscribe(this::executeTask);
                // 等待执行器结束
                synchronized (scheduledExecutorService) {
                    scheduledExecutorService.wait();
                }
                log.info("======all block has been replay, start balance query============== ");
                accounts.forEach((address, account) -> scheduledExecutorService.submit(() -> {
                    try {
                        BigInteger balance = getEthBalance(address);
                        BigInteger usdtBalance = getUsdtBalance(address);
                        account.setBalance(balance);
                        account.setUsdtBalance(usdtBalance);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));
                scheduledExecutorService.wait();
                log.info("======all block has been replay! total cost:{} s", (System.currentTimeMillis() - startMill) / 1000);
                stop();
            } catch (Exception e) {
                log.error("EthService throw Exception:", e);
            }
        } else {
            log.info("EthService is running!");
        }

    }

    @Override
    public void stop() {
        if (getServiceStatus() != ServiceStatus.Stopped) {
            if (web3Client != null) {
                web3Client.shutdown();
            }
            scheduledExecutorService.shutdown();
            setServiceStatus(ServiceStatus.Stopped);
            log.info("EthService will be stopped!");
        } else {
            log.info("EthService has been stopped!");
        }
    }

    private List<Request> generateRequests(List<BigInteger> blockNumbers) {
        log.info("request {}-{} block data===========", blockNumbers.get(0), blockNumbers.get(blockNumbers.size() - 1));
        return blockNumbers.stream().map(bigInteger -> new Request<>(
                "eth_getBlockByNumber",
                Arrays.asList(
                        Numeric.encodeQuantity(bigInteger),
                        true),
                this.httpService,
                EthBlock.class)).collect(Collectors.toList());
    }

    private Flowable<List<EthBlock>> getEthBlocksFlowable(List<Request> requests) {
        return new FlowableFromCallable<>(() -> httpService.sendBatch(requests, EthBlock.class));
    }

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
     * 区块数据任务
     *
     * @param batch
     */
    private void executeTask(List<BigInteger> batch) {
        scheduledExecutorService.submit(() -> {
            List<Request> requests = generateRequests(batch);
            try {
                List<EthBlock> ethBlocks = httpService.sendBatch(requests, EthBlock.class);
                ethBlocks.stream().flatMap(block -> toTransactions(block).stream()).forEach(transaction -> {
                    String from = transaction.getFrom();
                    String to = transaction.getTo();
//                                                    BigInteger value = trx.getValue();
                    if (from != null) {
                        accounts.put(from, new EthAccountEntity(from));
                    }
                    if (to != null) {
                        accounts.put(to, new EthAccountEntity(to));
                    }
                });
            } catch (IOException e) {
                log.error("获取区块数据错误", e);
            }
        });
    }
//() -> Flowable.just(batch).map(this::generateRequests)
//                        .flatMap(this::getEthBlocksFlowable)
//                        .retry(3)
//                        .flatMapIterable(ehtBlocks -> ehtBlocks)
//            .flatMapIterable(EthSnapshotServiceImpl::toTransactions).subscribe(new Subscriber<Transaction>() {
//        @Override
//        public void onSubscribe(Subscription s) {
//            s.request(Long.MAX_VALUE);//不限制请求事件数
//        }
//
//        @Override
//        public void onNext(Transaction trx) {
//            try {
////                                                    long _start = System.currentTimeMillis();
////                                                    List<EthTrxEntity> entities = transactions.stream().map(EthTrxEntity::fromTransaction).collect(Collectors.toList());
////                                                    ethTrxRepository.saveAll(entities);
////                                                    List<String> allToAddress = entities.stream().map(entity -> entity.getTrxTo()).collect(Collectors.toList());
////                                                    allToAddress.forEach(add->accounts.put(add,BigInteger.ZERO));
////                                                    log.info("save {} transaction data cost:{} s", entities.size(), (System.currentTimeMillis() - _start) / 1000);
//                String from = trx.getFrom();
//                String to = trx.getTo();
////                                                    BigInteger value = trx.getValue();
//                if (from != null) {
//                    accounts.put(from, new EthAccountEntity(from));
//                }
//                if (to != null) {
//                    accounts.put(to, new EthAccountEntity(to));
//                }
//            } catch (Exception e) {
//                log.error("save transaction data error：", e);
//            }
//        }
//
//        @Override
//        public void onError(Throwable t) {
//            log.error("onError:", t);
//        }
//
//        @Override
//        public void onComplete() {
//            log.info("=============block:{}-{} batch save complete!===================================", batch.get(0), batch.get(batch.size() - 1));
//        }
//    })

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
        org.web3j.protocol.core.methods.request.Transaction ethCallTransaction = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(address, "0xdac17f958d2ee523a2206206994597c13d831ec7", "0x27e235e3000000000000000000000000" + address);
        String rStr = web3Client.ethCall(ethCallTransaction, DefaultBlockParameterName.LATEST).send().getValue();
        return BigInteger.valueOf(Long.parseLong(rStr));
    }

    @Override
    public void destroy() throws Exception {
        stop();
    }
}

package top.wangjc.blockchain_snapshot.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import top.wangjc.blockchain_snapshot.document.OperationLogDocument;
import top.wangjc.blockchain_snapshot.service.BlockChainService;
import top.wangjc.blockchain_snapshot.utils.Counter;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractSnapshotService implements BlockChainService {
    // 服务状态
    private ServiceStatus serviceStatus = ServiceStatus.Stopped;

    public Date getStartTime() {
        return startTime;
    }

    // 任务开始时间
    private Date startTime;
    // 调度器
    private Scheduler scheduler;
    private ScheduledExecutorService scheduledExecutorService;
    // 主链类型
    public final ChainType chainType;
    private final String serviceName;

    public Counter getBlockCounter() {
        return blockCounter;
    }

    private Counter blockCounter;

    // 找到的账户地址
    @JsonIgnore
    public Set<String> foundAddress = new ConcurrentSkipListSet<>();

    protected AbstractSnapshotService(ChainType chainType, String serviceName) {
        this.chainType = chainType;
        this.serviceName = serviceName;
    }

    @Override
    public ServiceStatus getServiceStatus() {
        return serviceStatus;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    protected void setServiceStatus(ServiceStatus status) {
        serviceStatus = status;
    }

    @Override
    public void restart() {
        stop();
        start();
    }

    @Override
    public void start() {
        if (getServiceStatus() != ServiceStatus.Running) {
            setServiceStatus(ServiceStatus.Running);
            startTime = new Date();
            this.blockCounter = new Counter(chainType.name());
            executeBlockSyncTask();
        } else {
            log.info(" {} service is running!", chainType);
        }
    }

    /**
     * 执行区块同步任务
     */
    private void executeBlockSyncTask() {
        try {
            doInit();
            replayBlock(getStartBlock(), getEndBlock(), getBatchSize());
        } catch (Exception e) {
            log.error("执行区块同步任务错误：", e);
        }
    }


    /**
     * 重放区块
     *
     * @param endBlock
     * @param startBlock
     */
    private void replayBlock(int startBlock, int endBlock, int batchSize) {
        try {
            assert endBlock > startBlock;
            log.info("============ replay block {}-{} =================== ", startBlock, endBlock);
            // 分批次遍历区块
            Flowable.range(startBlock, endBlock - startBlock + 1)
                    .buffer(batchSize)
                    .parallel()
                    .runOn(scheduler)
                    .map(this::handleBatchBlock)
                    .sequential()
                    .subscribe(this::handleLog, this::handleError, this::handleLogComplete);
        } catch (Exception e) {
            log.error("replay block throw Exception:", e);
        }
    }

    /**
     * 处理任务完成
     */
    protected void handleLogComplete() {
        log.info("================= replay block complete ,total cost:{} s,get {} address!===============", (System.currentTimeMillis() - startTime.getTime()) / 1000, foundAddress.size());
        stop();
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
     * 处理日志
     *
     * @param operationLog
     */
    protected abstract void handleLog(OperationLogDocument operationLog);

    /**
     * 处理批量区块请求
     *
     * @param batchNums
     * @return
     */
    protected abstract OperationLogDocument handleBatchBlock(List<Integer> batchNums);

    /**
     * 获取开始区块
     *
     * @return
     */
    protected abstract int getStartBlock();

    /**
     * 获取结束区块
     *
     * @return
     */
    protected abstract int getEndBlock();

    /**
     * 获取每批次处理块数
     *
     * @return
     */
    protected abstract int getBatchSize();

    /**
     * 初始化
     */
    protected void doInit() {
        scheduledExecutorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());
        scheduler = Schedulers.from(scheduledExecutorService);
    }

    /**
     * 关闭
     */
    protected void doClose() {
        scheduledExecutorService.shutdown();
        try {
            if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
                if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Thread pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    protected void increaseCounter() {
        blockCounter.increse();
    }

    protected void increaseCounter(int num) {
        blockCounter.increse(num);
    }

    @Override
    public void stop() {
        if (getServiceStatus() != ServiceStatus.Stopped) {
            doClose();
            setServiceStatus(ServiceStatus.Stopped);
            blockCounter.reset();
            log.info("{} service will be stopped!", chainType);
        } else {
            log.info("{} service has been stopped!", chainType);
        }
    }


}

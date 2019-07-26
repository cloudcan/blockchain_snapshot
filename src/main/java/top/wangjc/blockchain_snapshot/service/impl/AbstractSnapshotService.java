package top.wangjc.blockchain_snapshot.service.impl;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.web3j.utils.Async;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;
import top.wangjc.blockchain_snapshot.service.BlockChainService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractSnapshotService implements BlockChainService {
    // 服务状态
    private ServiceStatus serviceStatus = ServiceStatus.Stopped;
    // 任务开始时间
    private long taskStartMill;
    // 调度器
    private Scheduler scheduler;
    private ScheduledExecutorService scheduledExecutorService;
    // 主链类型
    public final ChainType chainType;
    // 找到的账户地址
    public Set<String> foundAddress = new ConcurrentSkipListSet<>();

    protected AbstractSnapshotService(ChainType chainType) {
        this.chainType = chainType;
    }

    @Override
    public ServiceStatus getServiceStatus() {
        return serviceStatus;
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
            taskStartMill = System.currentTimeMillis();
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
            Flowable.range(startBlock, endBlock - startBlock+1)
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
        log.info("================= replay block complete ,total cost:{} s,get {} address!===============", (System.currentTimeMillis() - taskStartMill) / 1000, foundAddress.size());
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
     * @param operationLogEntity
     */
    protected abstract void handleLog(OperationLogEntity operationLogEntity);

    /**
     * 处理批量区块请求
     *
     * @param batchNums
     * @return
     */
    protected abstract OperationLogEntity handleBatchBlock(List<Integer> batchNums);

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
        scheduledExecutorService = Async.defaultExecutorService();
        scheduler = Schedulers.from(scheduledExecutorService);
    }

    /**
     * 关闭
     */
    protected void doClose() {
        scheduler.shutdown();
        try {
            scheduledExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void stop() {
        if (getServiceStatus() != ServiceStatus.Stopped) {
            doClose();
            setServiceStatus(ServiceStatus.Stopped);
            log.info("{} service will be stopped!", chainType);
        } else {
            log.info("{} service has been stopped!", chainType);
        }
    }

}

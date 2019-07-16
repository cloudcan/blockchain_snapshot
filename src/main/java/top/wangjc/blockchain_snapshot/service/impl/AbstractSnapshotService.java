package top.wangjc.blockchain_snapshot.service.impl;

import top.wangjc.blockchain_snapshot.service.SnapshotService;

public abstract class AbstractSnapshotService implements SnapshotService {
    // 服务状态
    private ServiceStatus serviceStatus;

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
}

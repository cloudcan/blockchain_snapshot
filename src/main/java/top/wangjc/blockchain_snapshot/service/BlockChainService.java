package top.wangjc.blockchain_snapshot.service;

public interface BlockChainService {
    // 启动服务
    void start();

    // 重启服务
    void restart();

    //停止服务
    void stop();

    // 获取服务状态
    ServiceStatus getServiceStatus();

    String getServiceName();


    // 服务状态
    enum ServiceStatus {
        Stopped, Running
    }

    // 主链类型
    enum ChainType {
        Ethereum, Tron, Bitcoin, Litecoin,Hcash
    }
}

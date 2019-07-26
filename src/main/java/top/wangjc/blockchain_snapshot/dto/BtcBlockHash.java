package top.wangjc.blockchain_snapshot.dto;

import org.web3j.protocol.core.Response;

public class BtcBlockHash extends Response<String> {
    public String getBlockHash() {
        return getResult();
    }
}

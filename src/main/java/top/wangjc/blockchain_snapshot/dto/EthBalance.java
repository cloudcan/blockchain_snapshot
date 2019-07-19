package top.wangjc.blockchain_snapshot.dto;

import org.web3j.protocol.core.Response;

import java.math.BigInteger;

public class EthBalance extends Response<String> {
    public BigInteger getBalance() {
        return new BigInteger(getResult().substring(2), 16);
    }

}

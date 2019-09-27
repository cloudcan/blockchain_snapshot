package top.wangjc.blockchain_snapshot.dto;

import org.web3j.protocol.core.Response;

import java.math.BigDecimal;
import java.math.BigInteger;

public class EthUsdtBalance extends Response<String> {
    public BigDecimal getBalance() {
        return new BigDecimal(new BigInteger(getResult().substring(2), 16));
    }

}

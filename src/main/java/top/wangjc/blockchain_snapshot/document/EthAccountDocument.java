package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Document("eth_account")
public class EthAccountDocument {
    private String id;
    /**
     * 地址
     */
    @Indexed
    private String address;
    /**
     * usdt 余额
     */
    private BigDecimal usdtBalance;
    /**
     * 余额
     */
    private BigDecimal ethBalance;

    /**
     * 更新时的区块高度
     */
    private Integer updateBlockHeight;

    public EthAccountDocument() {
    }


    public EthAccountDocument(String address, BigDecimal ethBalance, BigDecimal usdtBalance, int updateBlockHeight) {
        this.address = address;
        this.usdtBalance = usdtBalance;
        this.updateBlockHeight = updateBlockHeight;
        this.ethBalance = ethBalance;
    }

}

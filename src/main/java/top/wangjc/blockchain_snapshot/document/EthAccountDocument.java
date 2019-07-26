package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import org.apache.logging.log4j.util.Strings;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.Id;
import java.math.BigInteger;
import java.util.Date;

@Data
@Document("eth_account")
public class EthAccountDocument {
    private static final BigInteger ETH_LOW = BigInteger.valueOf(1000000000000000000l);
    @Id
    private String id;
    /**
     * 地址
     */
    @Indexed
    private String address;
    /**
     * usdt 余额
     */
    private BigInteger usdtBalance;
    /**
     * 余额低位
     */
    private BigInteger ethBalanceLow;
    /**
     * 余额高位
     */
    private BigInteger ethBalanceHigh;

    /**
     * 更新时间
     */
    private Date updateDate;
    /**
     * 更新时的区块高度
     */
    private Integer updateBlockHeight;

    public BigInteger getEthBalance() {
        return ethBalanceHigh.multiply(ETH_LOW).add(ethBalanceLow);
    }

    public void setEthBalance(BigInteger ethBalance) {
        this.ethBalanceHigh = ethBalance.divide(ETH_LOW);
        this.ethBalanceLow = ethBalance.subtract(ethBalanceHigh.multiply(ETH_LOW));
    }

    public EthAccountDocument() {
        this(Strings.EMPTY);
    }

    public EthAccountDocument(String address) {
        this(address, BigInteger.ZERO);
    }

    public EthAccountDocument(String address, BigInteger balance) {
        this(address, balance, BigInteger.ZERO);
    }

    public EthAccountDocument(String address, BigInteger ethBalance, BigInteger usdtBalance) {
        this.address = address;
        this.usdtBalance = usdtBalance;
        setEthBalance(ethBalance);
    }
}

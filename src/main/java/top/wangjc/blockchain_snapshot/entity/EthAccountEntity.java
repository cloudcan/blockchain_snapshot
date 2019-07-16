package top.wangjc.blockchain_snapshot.entity;

import lombok.Data;
import org.apache.logging.log4j.util.Strings;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigInteger;

@Data
@Entity(name = "eth_account")
public class EthAccountEntity {
    /**
     * usdt 余额
     */
    private BigInteger usdtBalance;
    /**
     * 地址
     */
    @Id
    private String address;
    /**
     * 余额
     */
    private BigInteger balance;

    public EthAccountEntity() {
        this(Strings.EMPTY);
    }

    public EthAccountEntity(String address) {
        this(address, BigInteger.ZERO);
    }

    public EthAccountEntity(String address, BigInteger balance) {
        this(address, balance, BigInteger.ZERO);
    }

    public EthAccountEntity(String address, BigInteger balance, BigInteger usdtBalance) {
        this.address = address;
        this.balance = balance;
        this.usdtBalance = usdtBalance;
    }
}

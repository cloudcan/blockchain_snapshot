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
     * 地址
     */
    @Id
    private String address;
    /**
     * usdt 余额
     */
    private BigInteger usdtBalance;
    /**
     * 余额
     */
    private BigInteger ethBalance;

    public EthAccountEntity() {
        this(Strings.EMPTY);
    }

    public EthAccountEntity(String address) {
        this(address, BigInteger.ZERO);
    }

    public EthAccountEntity(String address, BigInteger balance) {
        this(address, balance, BigInteger.ZERO);
    }

    public EthAccountEntity(String address, BigInteger ethBalance, BigInteger usdtBalance) {
        this.address = address;
        this.ethBalance = ethBalance;
        this.usdtBalance = usdtBalance;
    }
}

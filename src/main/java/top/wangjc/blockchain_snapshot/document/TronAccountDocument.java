package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import top.wangjc.blockchain_snapshot.dto.TronAccount;

import java.math.BigDecimal;
import java.math.BigInteger;

@Data
@Document("tron_account")
public class TronAccountDocument {
    private String id;

    @Indexed
    private String address;

    private BigDecimal tronBalance;

    private BigDecimal usdtBalance;

    private Integer updateBlockHeight;

    public TronAccountDocument(String address, BigDecimal tronBalance, BigDecimal usdtBalance) {
        this.address = address;
        this.tronBalance = tronBalance;
        this.usdtBalance = usdtBalance;
    }

    /**
     * 从tron账号生成
     *
     * @param account
     * @return
     */
    public static TronAccountDocument fromTronAccount(TronAccount account) {
        TronAccount.KeyValue keyValue = null;
        try {
            keyValue = account.getAsset().stream().filter(asset -> "USDT".equals(asset.getKey())).findAny().get();
        } catch (Exception e) {
        }
        return new TronAccountDocument(account.getAddress(), account.getBalance(), keyValue == null ? BigDecimal.ZERO : keyValue.getValue());
    }
}

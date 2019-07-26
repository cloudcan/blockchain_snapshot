package top.wangjc.blockchain_snapshot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import top.wangjc.blockchain_snapshot.dto.TronAccount;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "tron_account")
public class TronAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String address;
    private long trxBalance;
    private long usdtBalance;

    public TronAccountEntity(String address, long trxBalance, long usdtBalance) {
        this.address = address;
        this.trxBalance = trxBalance;
        this.usdtBalance = usdtBalance;
    }

    public static TronAccountEntity fromTronAccount(@NonNull TronAccount account) {
        TronAccount.KeyValue keyValue = null;
        try {
            keyValue = account.getAsset().stream().filter(asset -> "USDT".equals(asset.getKey())).findAny().get();
        } catch (Exception e) {
        }
        return new TronAccountEntity(account.getAddress(), account.getBalance(), keyValue == null ? 0 : keyValue.getValue());
    }
}

package top.wangjc.blockchain_snapshot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.Id;

@Data
@Entity(name = "btc_account")
@NoArgsConstructor
@AllArgsConstructor
public class BtcAccountEntity {
    /**
     * 地址
     */
    @Id
    private String address;
    /**
     * btc 余额
     */
    private long btcBalance;
}

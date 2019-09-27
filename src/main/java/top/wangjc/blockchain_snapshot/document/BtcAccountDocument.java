package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Document("btc_account")
@NoArgsConstructor
public class BtcAccountDocument {
    private String id;

    @Indexed
    private String address;

    private BigDecimal balance;

    private Integer updateBlockHeight;

    public BtcAccountDocument(String address, BigDecimal balance, Integer updateBlockHeight) {
        this.address = address;
        this.balance = balance;
        this.updateBlockHeight = updateBlockHeight;
    }

}

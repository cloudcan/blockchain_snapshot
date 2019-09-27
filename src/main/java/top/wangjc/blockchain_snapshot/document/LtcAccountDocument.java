package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@Document("ltc_account")
@NoArgsConstructor
public class LtcAccountDocument {
    private String id;

    @Indexed
    private String address;

    private BigDecimal balance;

    private Integer updateBlockHeight;

    public LtcAccountDocument(String address, BigDecimal balance, Integer updateBlockHeight) {
        this.address = address;
        this.balance = balance;
        this.updateBlockHeight = updateBlockHeight;
    }
}

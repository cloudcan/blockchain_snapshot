package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.Id;
import java.math.BigInteger;

@Data
@Document("btc_account")
public class BtcAccountDocument {
    @Id
    private String id;

    @Indexed
    private String address;

    private BigInteger balance;

    private Integer updateBlockHeight;
}

package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.xml.bind.DatatypeConverter;
import java.math.BigDecimal;
import java.security.MessageDigest;

@Document("btc_utxo")
@Data
public class BtcUTXODocument {
    private String id;
    //    @Indexed
//    private String blockHash;
//    @Indexed
//    private Integer blockHeight;
//    @Indexed
    private String txId;
    private Integer mintIndex;
    private BigDecimal mintValue;
    @Indexed
    private String address;
    @Indexed
    private String outPoint;
    private Boolean coinBase;
    @Indexed
    private String spentBlock;

    public static String generateOutPoint(String txId, int mintIndex) {
        String hashStr;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest((txId + mintIndex).getBytes());
            hashStr = DatatypeConverter.printHexBinary(digest).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("生成输出点错误");
        }
        return hashStr;
    }
}

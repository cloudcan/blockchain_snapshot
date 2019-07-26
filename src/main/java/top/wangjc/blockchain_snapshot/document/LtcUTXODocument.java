package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import javax.persistence.Id;
import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;

@Document("ltc_utxo")
@Data
public class LtcUTXODocument {
    @Id
    private String id;
    private String txHash;
    @Indexed
    private String address;
    @Indexed
    private String outPoint;
    private Long mintValue;
    private Integer mintIndex;
    private Boolean coinBase;
    private String spentBlock;

    public static String generateOutPoint(String txHash, int mintIndex) {
        String hashStr;
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest((txHash + mintIndex).getBytes());
            hashStr = DatatypeConverter.printHexBinary(digest).toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("生成输出点错误");
        }
        return hashStr;
    }
}

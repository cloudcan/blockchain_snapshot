package top.wangjc.blockchain_snapshot.entity;

import lombok.Data;

import javax.persistence.*;
import java.math.BigInteger;

@Entity
@Table(name = "btc_utxo")
@Data
public class BtcUTXOEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String txHash;
    private BigInteger mintValue;
    private Integer mintIndex;
    private Integer mintHeight;
    private Integer spentHeight;
    private Boolean coinbase;

}

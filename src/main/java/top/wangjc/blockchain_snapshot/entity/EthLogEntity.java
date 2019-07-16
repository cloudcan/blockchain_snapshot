package top.wangjc.blockchain_snapshot.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigInteger;
import java.util.Date;

@Data
@Entity(name = "eth_log")
public class EthLogEntity {
    @Id
    private int id;
    private BigInteger startBlock;
    private BigInteger endBlock;
    private Date createDate;

    public EthLogEntity(BigInteger startBlock, BigInteger endBlock) {
        this.startBlock = startBlock;
        this.endBlock = endBlock;
    }

    public EthLogEntity() {
    }
}

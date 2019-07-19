package top.wangjc.blockchain_snapshot.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import top.wangjc.blockchain_snapshot.service.SnapshotService;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigInteger;
import java.util.Date;

@Data
@Entity(name = "operation_log")
public class OperationLogEntity {
    @Id
    private int id;
    private BigInteger startBlock;
    private BigInteger endBlock;
    private Date createDate;
    private SnapshotService.ChainType chainType;

    public OperationLogEntity(BigInteger startBlock, BigInteger endBlock, SnapshotService.ChainType chainType) {
        this.startBlock = startBlock;
        this.endBlock = endBlock;
        this.chainType = chainType;
    }

    public OperationLogEntity() {
    }
}

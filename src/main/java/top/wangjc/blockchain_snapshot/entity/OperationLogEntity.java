package top.wangjc.blockchain_snapshot.entity;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import top.wangjc.blockchain_snapshot.service.BlockChainService;

import javax.persistence.*;
import java.util.Date;

@Data
@Entity(name = "operation_log")
@EntityListeners(AuditingEntityListener.class)
public class OperationLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private Integer startBlock;
    private Integer endBlock;
    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Date createDate;
    private BlockChainService.ChainType chainType;

    public OperationLogEntity(int startBlock, int endBlock, BlockChainService.ChainType chainType) {
        this.startBlock = startBlock;
        this.endBlock = endBlock;
        this.chainType = chainType;
    }

    public OperationLogEntity() {
    }
}

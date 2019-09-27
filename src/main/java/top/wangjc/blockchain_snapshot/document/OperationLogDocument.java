package top.wangjc.blockchain_snapshot.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import top.wangjc.blockchain_snapshot.service.BlockChainService;

import java.util.Date;

@Data
@Document("operation_log")
@NoArgsConstructor
public class OperationLogDocument {
    private String id;

    private Date opDate;

    private Integer startBlock;

    private Integer endBlock;

    private BlockChainService.ChainType chainType;


    public OperationLogDocument(int startBlock, int endBlock, BlockChainService.ChainType chainType) {
        this.opDate = new Date();
        this.startBlock = startBlock;
        this.endBlock = endBlock;
        this.chainType = chainType;
    }
}

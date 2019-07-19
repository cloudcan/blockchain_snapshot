package top.wangjc.blockchain_snapshot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.web3j.protocol.core.methods.response.Transaction;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "eth_transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EthTransactionEntity {
    @Id
    private String transactionHash;
    private String blockHash;
    private Long blockNumber;
    @Column(name = "transaction_index")
    private Long transactionIndex;
    private String transactionFrom;
    private String transactionTo;
    private Long transactionValue;

    public static EthTransactionEntity fromTransaction(Transaction transaction) {
        return new EthTransactionEntity(
                transaction.getHash(),
                transaction.getBlockHash(),
                transaction.getBlockNumber().longValue(),
                transaction.getTransactionIndex().longValue(),
                transaction.getFrom(), transaction.getTo(),
                transaction.getValue().longValue());
    }
}

package top.wangjc.blockchain_snapshot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.web3j.protocol.core.methods.response.Transaction;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigInteger;

@Entity(name = "eth_trx")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EthTrxEntity {
    @Id
    private String trxHash;
    private String blockHash;
    private BigInteger blockNumber;
    @Column(name = "trx_index")
    private BigInteger trxIndex;
    private String trxFrom;
    private String trxTo;
    private BigInteger trxValue;

    public static EthTrxEntity fromTransaction(Transaction transaction) {
        return new EthTrxEntity(
                transaction.getHash(),
                transaction.getBlockHash(),
                transaction.getBlockNumber(),
                transaction.getTransactionIndex(),
                transaction.getFrom(), transaction.getTo(),
                transaction.getValue());
    }
}

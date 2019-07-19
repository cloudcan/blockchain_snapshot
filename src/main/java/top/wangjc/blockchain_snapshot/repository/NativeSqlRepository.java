package top.wangjc.blockchain_snapshot.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.wangjc.blockchain_snapshot.entity.EthAccountEntity;
import top.wangjc.blockchain_snapshot.entity.EthTransactionEntity;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;

@Repository
public class NativeSqlRepository {
    @PersistenceContext
    EntityManager entityManager;

    /**
     * 批量插入以太坊账户
     */
    @Transactional
    public int batchInsertEthAccount(List<EthAccountEntity> accounts) {
        if (accounts.size() <= 0) return 0;
        StringBuilder delteSql = new StringBuilder("delete from eth_account where address in (");
        accounts.forEach(account -> delteSql.append("'" + account.getAddress() + "',"));
        delteSql.deleteCharAt(delteSql.lastIndexOf(","));
        delteSql.append(")");
        Query deleteQuery = entityManager.createNativeQuery(delteSql.toString());
        deleteQuery.executeUpdate();
        StringBuilder insertSql = new StringBuilder("insert into eth_account(address,balance,usdt_balance) values ");
        accounts.forEach(account -> insertSql.append(String.format("('%s','%s','%s'),", account.getAddress(), account.getEthBalance(), account.getUsdtBalance())));
        insertSql.deleteCharAt(insertSql.lastIndexOf(","));
        Query insertQuery = entityManager.createNativeQuery(insertSql.toString());
        return insertQuery.executeUpdate();
    }

    /**
     * 批量插入以太坊事务
     */
    @Transactional
    public int batchInsertEthTrx(List<EthTransactionEntity> trxs) {
        if (trxs.size() <= 0) return 0;
        StringBuilder deleteSql = new StringBuilder("delete from eth_trx where trx_hash in (");
        trxs.forEach(trx -> deleteSql.append("'" + trx.getTransactionHash() + "',"));
        deleteSql.deleteCharAt(deleteSql.lastIndexOf(","));
        deleteSql.append(")");
        Query deleteQuery = entityManager.createNativeQuery(deleteSql.toString());
        deleteQuery.executeUpdate();
        StringBuilder insertSql = new StringBuilder("insert into eth_trx(trx_hash,block_hash,block_number,trx_index,trx_from,trx_to,trx_value) values ");
        trxs.forEach(trx -> insertSql.append(String.format("('%s','%s',%d,%d,'%s','%s','%s'),", trx.getTransactionHash(), trx.getBlockHash(), trx.getBlockNumber(), trx.getTransactionIndex(), trx.getTransactionFrom(), trx.getTransactionTo(), trx.getTransactionValue().toString())));
        insertSql.deleteCharAt(insertSql.lastIndexOf(","));
        Query insertQuery = entityManager.createNativeQuery(insertSql.toString());
        return insertQuery.executeUpdate();
    }

}

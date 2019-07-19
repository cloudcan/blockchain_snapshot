package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.entity.EthTransactionEntity;

@Repository
public interface EthTransactionRepository extends JpaRepository<EthTransactionEntity, String> {

}

package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.entity.OperationLogEntity;
import top.wangjc.blockchain_snapshot.service.BlockChainService;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Integer> {
    @Query(value = "SELECT * FROM operation_log WHERE chain_type=:chainType ORDER BY create_date DESC LIMIT 1", nativeQuery = true)
    OperationLogEntity findLastLogByChainType(@Param("chainType") BlockChainService.ChainType chainType);
}

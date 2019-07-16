package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.entity.EthLogEntity;

@Repository
public interface EthLogRepository extends JpaRepository<EthLogEntity, Integer> {
    @Query(value = "SELECT *,MAX(end_block) FROM eth_log GROUP BY id",nativeQuery = true)
    public EthLogEntity findLastLog();
}

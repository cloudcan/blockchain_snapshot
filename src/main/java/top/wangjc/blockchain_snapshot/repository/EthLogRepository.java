package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.entity.EthLogEntity;

@Repository
public interface EthLogRepository extends JpaRepository<EthLogEntity, Integer> {
    @Query(value = "SELECT * FROM eth_log WHERE success=1 ORDER BY create_date DESC LIMIT 1",nativeQuery = true)
    public EthLogEntity findLastLog();
}

package top.wangjc.blockchain_snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.entity.EthTrxEntity;
@Repository
public interface EthTrxRepository extends JpaRepository<EthTrxEntity,String> {
}

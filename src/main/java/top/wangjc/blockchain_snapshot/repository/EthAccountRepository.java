package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.entity.EthAccountEntity;
@Repository
public interface EthAccountRepository extends JpaRepository<EthAccountEntity,String> {
}

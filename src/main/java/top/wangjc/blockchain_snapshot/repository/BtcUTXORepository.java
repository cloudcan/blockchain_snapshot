package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.wangjc.blockchain_snapshot.entity.BtcUTXOEntity;

@Repository
public interface BtcUTXORepository extends JpaRepository<BtcUTXOEntity, Integer> {
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "update btc_utxo set spent_height=:spentHeight where tx_hash=:txHash and mint_index=:mintIndex")
    void updateUTXOByOutPoint(@Param("spentHeight") int spentHeight, @Param("txHash") String txHash, @Param("mintIndex") int mintIndex);
}

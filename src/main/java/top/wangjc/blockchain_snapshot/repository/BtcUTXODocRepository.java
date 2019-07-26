package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.document.BtcUTXODocument;

@Repository
public interface BtcUTXODocRepository extends MongoRepository<BtcUTXODocument, String> {
}

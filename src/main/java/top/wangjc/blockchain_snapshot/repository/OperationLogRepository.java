package top.wangjc.blockchain_snapshot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import top.wangjc.blockchain_snapshot.document.OperationLogDocument;

@Repository
public interface OperationLogRepository extends MongoRepository<OperationLogDocument, String> {
}

package top.wangjc.blockchain_snapshot.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import top.wangjc.blockchain_snapshot.document.BtcUTXODocument;

import java.util.List;

@Component
public class MongoRepositoryImpl {
    @Autowired
    private MongoTemplate mongoTemplate;


    public long deleteBtcUTXOByOutpoint(List<String> outPoints) {
        Query query = Query.query(Criteria.where("out_point").in(outPoints));
        return mongoTemplate.remove(query, BtcUTXODocument.class).getDeletedCount();
    }

    /**
     * 批量保存
     *
     * @param documents
     */
    public void batchSave(List documents) {
        mongoTemplate.insertAll(documents);
    }
}

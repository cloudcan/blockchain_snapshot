package top.wangjc.blockchain_snapshot.repository;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;

@Component
public class MongoRepositoryImpl {
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 修改utxo
     *
     * @param outpoints
     * @param spentBlock
     * @return
     */
    public <T> long updateUTXOByOutpoint(List<String> outpoints, String spentBlock, Class<T> clazz) {
        if (outpoints.size() > 0) {
            Query query = Query.query(Criteria.where("outPoint").in(outpoints));
            Update update = new Update();
            update.set("spentBlock", spentBlock);
            return mongoTemplate.updateMulti(query, update, clazz).getModifiedCount();
        } else {
            return 0;
        }

    }

    /**
     * 批量保存
     *
     * @param documents
     */
    public void batchSave(List documents) {
        mongoTemplate.insertAll(documents);
    }

    /**
     * 获取utxo结合
     *
     * @param batch
     * @param clazz
     * @param <T>
     * @return
     */
    public <T> List<T> getUTXOList(List<Integer> batch, Class<T> clazz) {
        Query query = Query.query(Criteria.where("blockHeight").in(batch).and("spentBlock").is("").and("address").ne(""));
        return mongoTemplate.find(query, clazz);
    }

    /**
     * 获取所有utxo
     *
     * @param collectionName
     * @return
     */
    public Iterator<Document> getAllUTXOList(String collectionName) {
        BsonDocument bson = new BsonDocument();
        bson.append("spentBlock", new BsonDocument().append("$eq", new BsonString("")))
                .append("address", new BsonDocument().append("$ne", new BsonString("")));
        return mongoTemplate.getCollection(collectionName).find(bson).batchSize(1000).iterator();
    }
}

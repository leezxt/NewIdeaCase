package com.newideacase.platform.catalog.infrastructure;

import com.newideacase.platform.catalog.domain.ProductCursor;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoProductQueryRepository implements ProductQueryRepository {

    private static final Sort PAGE_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("_id"));

    private final MongoTemplate mongoTemplate;

    public MongoProductQueryRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ProductDocument> findPage(ProductCursor cursor, int size) {
        Query query = new Query().with(PAGE_SORT).limit(size);
        if (cursor != null) {
            Criteria olderTimestamp = Criteria.where("createdAt").lt(cursor.createdAt());
            Criteria sameTimestampOlderId = new Criteria().andOperator(
                    Criteria.where("createdAt").is(cursor.createdAt()),
                    Criteria.where("_id").lt(new ObjectId(cursor.id())));
            query.addCriteria(new Criteria().orOperator(olderTimestamp, sameTimestampOlderId));
        }
        return mongoTemplate.find(query, ProductDocument.class);
    }
}

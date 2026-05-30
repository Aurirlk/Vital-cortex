package cn.kmbeast.crm.vectordb;

import java.util.List;
import java.util.Map;

public interface LocalVectorStore {

    void createCollection(String collectionName);

    boolean collectionExists(String collectionName);

    void upsert(String collectionName, String id, String content, Map<String, Object> metadata);

    void batchUpsert(String collectionName, List<VectorEntity> documents);

    List<SearchResult> search(String collectionName, String query, int topK);

    void delete(String collectionName, int id);

    void deleteCollection(String collectionName);

    Map<String, Object> getStats();

    Map<String, Object> getCollectionStats(String collectionName);

    void compact();

    int getCompactCount(String collectionName);

    class SearchResult {
        public int id;
        public String content;
        public Map<String, Object> metadata;
        public float score;

        public SearchResult(int id, String content, Map<String, Object> metadata, float score) {
            this.id = id;
            this.content = content;
            this.metadata = metadata;
            this.score = score;
        }
    }
}

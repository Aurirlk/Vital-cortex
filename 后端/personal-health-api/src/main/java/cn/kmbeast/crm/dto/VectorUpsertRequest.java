package cn.kmbeast.crm.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VectorUpsertRequest {

    private String collection;

    private List<DocumentItem> documents;

    @Data
    public static class DocumentItem {
        private String content;
        private Map<String, Object> metadata;
    }
}

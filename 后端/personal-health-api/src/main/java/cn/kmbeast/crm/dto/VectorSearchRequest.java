package cn.kmbeast.crm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VectorSearchRequest {

    private String collection;

    private String query;

    @Builder.Default
    private int topK = 5;
}

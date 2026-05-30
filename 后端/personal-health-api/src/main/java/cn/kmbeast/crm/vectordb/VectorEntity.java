package cn.kmbeast.crm.vectordb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VectorEntity {

    private int id;

    private String content;

    private Map<String, Object> metadata;

    private float[] embedding;
}

package cn.kmbeast.crm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrmChatResponse {

    private String reply;

    private Integer rounds;

    @Builder.Default
    private List<String> toolsUsed = new ArrayList<>();

    private String sessionId;

    private Boolean isNewUser;
}

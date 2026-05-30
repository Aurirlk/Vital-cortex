package cn.kmbeast.crm.dto;

import lombok.Data;

@Data
public class CrmChatRequest {

    private String phoneNumber;

    private String query;

    private String sessionId;
}

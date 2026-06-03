package cn.kmbeast.service;

import java.util.List;

/**
 * Dify工作流服务
 * 调用Dify工作流提取关键词
 */
public interface DifyWorkflowService {

    /**
     * 从用户消息中提取健康领域关键词
     * @param userMessage 用户原始消息
     * @return 关键词列表
     */
    List<String> extractKeywords(String userMessage);
}

package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.PostReply;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostReplyVO extends PostReply {
    /** 用户名 */
    private String userName;
    /** 用户头像 */
    private String userAvatar;
}

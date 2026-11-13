package cn.kmbeast.pojo.vo;

import cn.kmbeast.pojo.entity.Post;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PostVO extends Post {
    /** 用户名 */
    private String userName;
    /** 用户头像 */
    private String userAvatar;
    /** 标签名 */
    private String tagName;
    /** 当前用户是否点赞 */
    private Boolean liked;
    /** 当前用户是否收藏 */
    private Boolean favorited;
}

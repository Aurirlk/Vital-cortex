package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.UserQueryDto;
import cn.kmbeast.pojo.entity.User;
import cn.kmbeast.pojo.vo.NotifyPreferenceVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户持久化接口
 */
public interface UserMapper {


    /**
     * 用户信息新增
     *
     * @param userInsert 用户信息
     * @return int 受影响行数
     */
    int insert(User userInsert);

    /**
     * 分页查询用户信息
     *
     * @param userQueryDto 分页查询参数
     * @return List<User>
     */
    List<User> query(UserQueryDto userQueryDto);

    /**
     * 查询满足分页查询的记录总数
     *
     * @param userQueryDto 分页查询参数
     * @return int 数据总数
     */
    int queryCount(UserQueryDto userQueryDto);

    /**
     * 更新用户信息
     *
     * @param user 用户信息
     * @return int 受影响行数
     */
    int update(User user);

    /**
     * 保存用户设置（2026-10-10 新增）
     *
     * <p>与通用 {@link #update} 分开：这里只更新设置列，且非空字段才覆盖，
     * 供设置页「每次只提交一组设置」的局部保存用；通用 update 服务于编辑资料，
     * 两者共用一条语句会让每次资料保存都被迫带上全部设置列。
     *
     * @param user 必须含 id，其余字段只填需要更新的设置项
     * @return int 受影响行数
     */
    int updateSettings(User user);

    /**
     * 批量删除用户信息
     *
     * @param ids 用户ID集合
     */
    void batchDelete(@Param(value = "ids") List<Integer> ids);

    /**
     * 根据不为空的查询信息查找用户
     *
     * @param user 参数
     * @return User
     */
    User getByActive(User user);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return User
     */
    User getUserById(@Param("userId") Integer userId);

    /**
     * 根据手机号查找用户
     *
     * <p>2026-10-03 修正：原实现拿手机号去匹配 {@code user_account}（逻辑账号 admin/lily），
     * 永远不会命中，导致短信登录 100% 失败。现匹配真正的 {@code phone} 字段。
     *
     * @param phone 手机号
     * @return User；不存在时返回 null
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * 统一登录标识查找：账号或手机号任一命中即可
     *
     * <p>用于登录页「账号登录」与「短信登录」共用一套后端逻辑。
     *
     * @param identifier 账号或手机号
     */
    User findByIdentifier(@Param("identifier") String identifier);

    /**
     * 患者选择器远程搜索（仅普通用户）
     *
     * <p>支持按姓名 / 账号 / 手机号（含后4位）模糊匹配，
     * 供 {@code <PatientSelect>} 组件与医生端「我的患者」使用。
     *
     * @param keyword 关键字
     * @param limit   返回条数上限
     */
    List<User> searchForSelect(@Param("keyword") String keyword,
                               @Param("limit") Integer limit);

    /**
     * 手机号占用校验
     *
     * @param phone     手机号
     * @param excludeId 排除的用户ID（编辑资料时传自身ID），可为 null
     * @return 已占用的记录数
     */
    int countByPhone(@Param("phone") String phone,
                     @Param("excludeId") Integer excludeId);

    /**
     * 只取用户的通知开关（2026-10-10 新增）
     *
     * <p>发通知前要判断收件人是否关掉了这一类，属于高频路径，
     * 因此单独一条只取五列的查询，不用 {@link #getUserById} 把整行
     * （含 user_pwd）读出来。
     *
     * @param userId 用户ID
     * @return 五列开关；用户不存在时返回 null
     */
    NotifyPreferenceVO getNotifyPreference(@Param("userId") Integer userId);

}

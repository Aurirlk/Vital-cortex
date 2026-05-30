package cn.kmbeast.aop;

import cn.kmbeast.pojo.dto.query.base.QueryDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 分页参数处理切面
 * 自动转换分页参数并限制最大页大小
 */
@Aspect
@Component
public class PagerAspect {

    /**
     * 最大每页记录数
     */
    private static final int MAX_PAGE_SIZE = 100;

    @Around("@annotation(pager)")
    public Object handlePageableParams(ProceedingJoinPoint joinPoint, Pager pager) throws Throwable {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof QueryDto) {
                QueryDto queryDTO = (QueryDto) arg;
                configPager(queryDTO);
            }
        }
        return joinPoint.proceed(args);
    }

    /**
     * 分页参数转换逻辑
     * 限制最大页大小，防止恶意请求
     */
    private void configPager(QueryDto queryDTO) {
        if (queryDTO.getCurrent() != null && queryDTO.getSize() != null) {
            // 限制最大页大小
            if (queryDTO.getSize() > MAX_PAGE_SIZE) {
                queryDTO.setSize(MAX_PAGE_SIZE);
            }
            // 确保页码和大小为正数
            if (queryDTO.getCurrent() < 1) {
                queryDTO.setCurrent(1);
            }
            if (queryDTO.getSize() < 1) {
                queryDTO.setSize(10);
            }
            // 转换为0-based索引
            queryDTO.setCurrent((queryDTO.getCurrent() - 1) * queryDTO.getSize());
        }
    }
}

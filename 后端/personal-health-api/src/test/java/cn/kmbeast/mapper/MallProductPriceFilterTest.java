package cn.kmbeast.mapper;

import cn.kmbeast.pojo.dto.query.extend.MallProductQueryDto;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 商城价格区间筛选 —— 2026-10-13（阶段 4）新增。
 *
 * <p><b>它守的是什么</b>：价格条件必须<b>同时</b>出现在 {@code query} 和
 * {@code queryCount} 两段 SQL 里，并且判空口径一致。这两段分居文件上下、长得几乎一样，
 * 是「改一处漏一处」的高发区。一旦漏改，症状非常隐蔽：</p>
 *
 * <ul>
 *   <li>只有 {@code query} 加了条件 → 列表被筛过，总数却是全表数，
 *       翻到最后一页是空的；</li>
 *   <li>只有 {@code queryCount} 加了条件 → 分页器显示的页数比实际内容少，
 *       用户永远看不到后面的商品。</li>
 * </ul>
 *
 * <p>两种都不会报错、不会有日志，只会让「数量对不上」。靠人眼比对两段类似 SQL
 * 是不可靠的，所以这里把它变成一条会失败的断言。</p>
 *
 * <p><b>怎么做的</b>：用 MyBatis 的 {@link Configuration} 解析 XML，
 * 取到 {@code BoundSql}，即<b>经过动态标签求值后的最终 SQL</b>（{@code ?} 占位版）。
 * 这不需要数据库连接，却真的执行了 {@code <if>} 的判断逻辑 ——
 * 所以它验证的是「条件真的会拼进去」，而不是「文本里恰好出现了这几个字」。
 * 与之相对，{@code MapperXmlParseTest} 只验语法能解析、语句 id 对得上，
 * 两者不重叠。</p>
 */
@DisplayName("商城价格区间筛选：query 与 queryCount 必须一致")
class MallProductPriceFilterTest {

    private static Configuration configuration;

    @BeforeAll
    static void loadMapperXml() throws Exception {
        Path file = Paths.get("src", "main", "resources", "mapper", "MallProductMapper.xml");
        assertTrue(Files.isRegularFile(file),
                "找不到 MallProductMapper.xml，测试工作目录不是模块根目录？实际: "
                        + file.toAbsolutePath());
        configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream in = Files.newInputStream(file)) {
            new XMLMapperBuilder(in, configuration, file.toString(),
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    @DisplayName("只传下限：两段 SQL 都出现 price >= ?，且都不出现上限条件")
    void minPriceOnly() {
        MallProductQueryDto dto = new MallProductQueryDto();
        dto.setMinPrice(new BigDecimal("9.90"));

        String query = sqlOf("query", dto);
        String count = sqlOf("queryCount", dto);

        assertTrue(query.contains("p.price >= ?"),
                "query 里没有下限条件，滑块拖了但列表不筛。实际 SQL:\n" + query);
        assertTrue(count.contains("p.price >= ?"),
                "queryCount 里没有下限条件，分页总数会大于实际列表。实际 SQL:\n" + count);

        assertFalse(query.contains("p.price <= ?"),
                "只传了下限，却拼出了上限条件 —— 会把高价商品一起筛掉。实际 SQL:\n" + query);
        assertFalse(count.contains("p.price <= ?"),
                "queryCount 多拼了上限条件，与 query 不一致。实际 SQL:\n" + count);
    }

    @Test
    @DisplayName("只传上限：两段 SQL 都出现 price <= ?，且都不出现下限条件")
    void maxPriceOnly() {
        MallProductQueryDto dto = new MallProductQueryDto();
        dto.setMaxPrice(new BigDecimal("199.00"));

        String query = sqlOf("query", dto);
        String count = sqlOf("queryCount", dto);

        assertTrue(query.contains("p.price <= ?"), "query 里没有上限条件。实际 SQL:\n" + query);
        assertTrue(count.contains("p.price <= ?"), "queryCount 里没有上限条件。实际 SQL:\n" + count);
        assertFalse(query.contains("p.price >= ?"), "只传上限却拼出了下限条件。实际 SQL:\n" + query);
        assertFalse(count.contains("p.price >= ?"), "queryCount 多拼了下限条件。实际 SQL:\n" + count);
    }

    @Test
    @DisplayName("上下限都传：两段 SQL 的条件个数与顺序一致")
    void bothBounds() {
        MallProductQueryDto dto = new MallProductQueryDto();
        dto.setMinPrice(new BigDecimal("9.90"));
        dto.setMaxPrice(new BigDecimal("199.00"));

        String query = sqlOf("query", dto);
        String count = sqlOf("queryCount", dto);

        assertTrue(query.contains("p.price >= ?"), "query 缺下限。实际 SQL:\n" + query);
        assertTrue(query.contains("p.price <= ?"), "query 缺上限。实际 SQL:\n" + query);
        assertTrue(count.contains("p.price >= ?"), "queryCount 缺下限。实际 SQL:\n" + count);
        assertTrue(count.contains("p.price <= ?"), "queryCount 缺上限。实际 SQL:\n" + count);

        // 顺序也要一致：先下限后上限。查询计划与「人读两段 SQL 做比对」都依赖这个顺序
        assertTrue(query.indexOf("p.price >= ?") < query.indexOf("p.price <= ?"),
                "query 里上下限顺序反了。实际 SQL:\n" + query);
        assertTrue(count.indexOf("p.price >= ?") < count.indexOf("p.price <= ?"),
                "queryCount 里上下限顺序反了。实际 SQL:\n" + count);
    }

    /**
     * 不传价格时不能凭空多出价格条件。
     *
     * <p>这条看着像废话，其实是最容易踩的：如果把 {@code null} 当成 0 参与比较
     * （或把 {@code <if test="minPrice != null">} 错写成 {@code test="minPrice != ''"}），
     * 每次不带价格的查询都会多一道恒真条件。它不影响结果正确性，
     * 却会让 SQL 文本每次都不同、不利于慢查询归并，也说明判空写错了。
     */
    @Test
    @DisplayName("不传价格：两段 SQL 都不出现任何价格条件")
    void noPriceFilter() {
        MallProductQueryDto dto = new MallProductQueryDto();

        String query = sqlOf("query", dto);
        String count = sqlOf("queryCount", dto);

        assertFalse(query.contains("p.price >=") || query.contains("p.price <="),
                "没有传价格，query 却拼出了价格条件（判空写错了）。实际 SQL:\n" + query);
        assertFalse(count.contains("p.price >=") || count.contains("p.price <="),
                "没有传价格，queryCount 却拼出了价格条件。实际 SQL:\n" + count);
    }

    /**
     * 价格与其它筛选条件并存时，价格条件不能被覆盖掉。
     * 顺带确认 SQL 仍然带 LIMIT —— 分页没被顺手改坏。
     */
    @Test
    @DisplayName("与关键字/分类筛选叠加：价格条件保留，分页 LIMIT 仍在")
    void priceFilterCoexistsWithOthers() {
        MallProductQueryDto dto = new MallProductQueryDto();
        dto.setKeyword("维生素");
        dto.setCategoryId(3);
        dto.setMinPrice(new BigDecimal("9.90"));
        dto.setMaxPrice(new BigDecimal("199.00"));
        dto.setCurrent(1);
        dto.setSize(10);

        String query = sqlOf("query", dto);
        String count = sqlOf("queryCount", dto);

        assertTrue(query.contains("p.price >= ?") && query.contains("p.price <= ?"),
                "叠加其它条件后价格条件丢了。实际 SQL:\n" + query);
        assertTrue(count.contains("p.price >= ?") && count.contains("p.price <= ?"),
                "叠加其它条件后 queryCount 的价格条件丢了。实际 SQL:\n" + count);

        assertTrue(query.contains("p.category_id = ?"), "分类条件丢了。实际 SQL:\n" + query);
        assertTrue(query.contains("LIKE CONCAT('%', ?, '%')"), "关键字条件丢了。实际 SQL:\n" + query);
        assertTrue(query.contains("LIMIT ?"), "分页 LIMIT 没了。实际 SQL:\n" + query);

        // COUNT 查询不该带 LIMIT，否则统计出来的是「当页条数」而不是总数
        assertFalse(count.contains("LIMIT"),
                "queryCount 里出现了 LIMIT —— 总数会变成当页条数。实际 SQL:\n" + count);
    }

    // ================================================================ 工具

    /**
     * 取某条语句在给定参数下<b>最终拼出来的 SQL</b>。
     *
     * <p>参数用 {@code null} 作为 statementId 之外的唯一实参传进去：
     * 单参数语句 MyBatis 会把对象直接当参数根，正好让 XML 里的
     * {@code test="minPrice != null"} 读到 DTO 的属性。</p>
     */
    private static String sqlOf(String statementId, MallProductQueryDto dto) {
        String fullId = MallProductMapper.class.getName() + "." + statementId;
        assertTrue(configuration.hasStatement(fullId),
                statementId + " 没有注册 —— 语句 id 与接口方法对不上了");
        MappedStatement statement = configuration.getMappedStatement(fullId);
        BoundSql boundSql = statement.getBoundSql(dto);
        return boundSql.getSql();
    }
}

package cn.kmbeast.service.impl;

import cn.kmbeast.mapper.DrugMapper;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.dto.query.extend.DrugQueryDto;
import cn.kmbeast.pojo.entity.Drug;
import cn.kmbeast.pojo.vo.DrugVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DrugServiceImpl单元测试
 */
@ExtendWith(MockitoExtension.class)
class DrugServiceImplTest {

    @Mock
    private DrugMapper drugMapper;

    @InjectMocks
    private DrugServiceImpl drugService;

    private Drug testDrug;
    private DrugVO testDrugVO;

    @BeforeEach
    void setUp() {
        testDrug = new Drug();
        testDrug.setId(1);
        testDrug.setName("测试药品");
        testDrug.setGenericName("测试通用名");
        testDrug.setCategory("测试分类");
        testDrug.setDescription("测试描述");
        testDrug.setPrice(BigDecimal.valueOf(10.0));
        testDrug.setUnit("盒");
        testDrug.setSpecification("10mg*10片");
        testDrug.setManufacturer("测试厂家");
        testDrug.setIsOtc(true);
        testDrug.setStock(100);
        testDrug.setStatus(true);

        testDrugVO = new DrugVO();
        testDrugVO.setId(1);
        testDrugVO.setName("测试药品");
        testDrugVO.setCategory("测试分类");
        testDrugVO.setPrice(BigDecimal.valueOf(10.0));
    }

    @Test
    void testSave() {
        // Given
        Drug drug = new Drug();
        drug.setName("新药品");

        // When
        Result<Void> result = drugService.save(drug);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(drugMapper).save(any(Drug.class));
        assertNotNull(drug.getCreateTime());
        assertTrue(drug.getStatus());
    }

    @Test
    void testGetById_Exists() {
        // Given
        when(drugMapper.getById(1)).thenReturn(testDrugVO);

        // When
        Result<DrugVO> result = drugService.getById(1);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("测试药品", result.getData().getName());
    }

    @Test
    void testGetById_NotExists() {
        // Given
        when(drugMapper.getById(999)).thenReturn(null);

        // When
        Result<DrugVO> result = drugService.getById(999);

        // Then
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void testQuery() {
        // Given
        DrugQueryDto queryDto = new DrugQueryDto();
        List<DrugVO> drugs = Arrays.asList(testDrugVO);
        when(drugMapper.query(queryDto)).thenReturn(drugs);
        when(drugMapper.queryCount(queryDto)).thenReturn(1);

        // When
        Result<List<DrugVO>> result = drugService.query(queryDto);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testSubscribe_Success() {
        // Given
        Integer drugId = 1;
        Integer quantity = 1;
        Integer userId = 1;
        when(drugMapper.getSubscribedDrugs(userId)).thenReturn(Arrays.asList());

        // When
        Result<Void> result = drugService.subscribe(drugId, quantity, userId);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(drugMapper).subscribe(any());
    }

    @Test
    void testSubscribe_AlreadySubscribed() {
        // Given
        Integer drugId = 1;
        Integer quantity = 1;
        Integer userId = 1;
        when(drugMapper.getSubscribedDrugs(userId)).thenReturn(Arrays.asList(testDrugVO));

        // When
        Result<Void> result = drugService.subscribe(drugId, quantity, userId);

        // Then
        assertNotNull(result);
        assertEquals(400, result.getCode());
        verify(drugMapper, never()).subscribe(any());
    }

    @Test
    void testSearch() {
        // Given
        String keyword = "测试";
        List<DrugVO> drugs = Arrays.asList(testDrugVO);
        when(drugMapper.searchByName(keyword, 20)).thenReturn(drugs);

        // When
        Result<List<DrugVO>> result = drugService.search(keyword);

        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }
}

package cn.kmbeast.service.impl;

import cn.kmbeast.context.LocalThreadHolder;
import cn.kmbeast.core.notify.NotifyTemplates;
import cn.kmbeast.core.notify.UserNotifier;
import cn.kmbeast.mapper.*;
import cn.kmbeast.pojo.api.Result;
import cn.kmbeast.pojo.em.NotificationSource;
import cn.kmbeast.pojo.entity.*;
import cn.kmbeast.pojo.vo.AppointmentVO;
import cn.kmbeast.pojo.vo.DoctorVO;
import cn.kmbeast.pojo.vo.MallOrderVO;
import cn.kmbeast.pojo.vo.ShoppingCartVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 业务通知接线回归测试 —— 2026-10-10 新增。
 *
 * <p><b>为什么单独写一个「接线」测试</b>：本轮把设置页三个通知开关背后补了实现，
 * 但真正的风险不在 {@code UserNotifier} 本身（那个有单测），而在
 * <b>六个业务触发点有没有记得调用它</b>：挂号成功、预约被取消、就诊完成、
 * 下单、支付、管理员改订单状态。这些位置在正常使用中「多一条少一条通知」
 * 完全不影响功能，所以最容易在后续重构里被顺手删掉而无人察觉。
 *
 * <p>顺带守住两条「不该发」的规则：
 * <ul>
 *   <li>自己取消自己的预约 → 不通知（他刚做完这个动作，再推一条是噪音）；</li>
 *   <li>管理员把订单改回 0/1 这类非流转状态 → 不通知
 *       （下单/支付各自的即时通知已经发过）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("业务通知接线")
class BusinessNotifyWiringTest {

    private static final Integer PATIENT = 1001;
    private static final Integer DOCTOR = 2001;
    private static final Integer ADMIN = 1;

    // ==================== 挂号 ====================

    @Mock private DepartmentMapper departmentMapper;
    @Mock private HospitalDoctorMapper doctorMapper;
    @Mock private DoctorScheduleMapper scheduleMapper;
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private VisitRecordMapper visitRecordMapper;
    @InjectMocks private AppointmentServiceImpl appointmentService;

    // ==================== 商城 ====================

    @Mock private ProductCategoryMapper categoryMapper;
    @Mock private MallProductMapper productMapper;
    @Mock private ShoppingCartMapper cartMapper;
    @Mock private MallOrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private ShippingAddressMapper addressMapper;
    @InjectMocks private MallServiceImpl mallService;

    // ==================== 共用 ====================

    @Mock private UserNotifier userNotifier;

    @AfterEach
    void tearDown() {
        LocalThreadHolder.clear();
    }

    // ================================================================ 挂号

    @Test
    @DisplayName("挂号成功：给患者发「预约成功」通知")
    void bookingShouldNotifyPatient() {
        prepareBooking();
        LocalThreadHolder.setUserId(PATIENT, 2);

        Appointment request = new Appointment();
        request.setScheduleId(31);
        Result<Void> result = appointmentService.bookAppointment(request);

        assertEquals(200, result.getCode());

        ArgumentCaptor<Integer> receiver = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<NotificationSource> source = ArgumentCaptor.forClass(NotificationSource.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> bizId = ArgumentCaptor.forClass(Long.class);
        verify(userNotifier).send(receiver.capture(), source.capture(), title.capture(),
                anyString(), bizId.capture(), eq(NotifyTemplates.LINK_APPOINTMENT));

        assertEquals(PATIENT, receiver.getValue(), "通知发给挂号人本人");
        assertEquals(NotificationSource.APPOINTMENT, source.getValue());
        assertEquals(NotifyTemplates.T_APPOINTMENT_BOOKED, title.getValue());
        assertEquals(900L, bizId.getValue(), "biz_id 用预约ID（save 回填的生成主键）");
    }

    @Test
    @DisplayName("挂号失败（号源已满）：不能发通知，因为压根没有预约")
    void failedBookingShouldNotNotify() {
        prepareBooking();
        when(scheduleMapper.reserveSlot(anyInt())).thenReturn(0);
        LocalThreadHolder.setUserId(PATIENT, 2);

        Appointment request = new Appointment();
        request.setScheduleId(31);
        Result<Void> result = appointmentService.bookAppointment(request);

        assertNotEquals(200, result.getCode());
        verify(userNotifier, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("管理员替患者取消预约：通知患者（这是他被取消的唯一提示）")
    void adminCancellingShouldNotifyPatient() {
        AppointmentVO existing = new AppointmentVO();
        existing.setId(66);
        existing.setPatientId(PATIENT);
        existing.setDoctorId(DOCTOR);
        existing.setScheduleId(31);
        existing.setStatus(0);
        existing.setAppointmentDate(LocalDate.of(2026, 10, 20));
        existing.setTimeSlot("afternoon");
        existing.setDoctorName("李医生");
        when(appointmentMapper.getById(66)).thenReturn(existing);
        LocalThreadHolder.setUserId(ADMIN, 1);

        Result<Void> result = appointmentService.cancelAppointment(66, "医生停诊");

        assertEquals(200, result.getCode());
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(userNotifier).send(eq(PATIENT), eq(NotificationSource.APPOINTMENT),
                title.capture(), content.capture(), eq(66L), eq(NotifyTemplates.LINK_APPOINTMENT));
        assertEquals(NotifyTemplates.T_APPOINTMENT_CANCELLED, title.getValue());
        assertTrue(content.getValue().contains("下午"), "正文带时段: " + content.getValue());
    }

    @Test
    @DisplayName("自己取消自己的预约：不通知（避免自言自语）")
    void selfCancellingShouldNotNotify() {
        AppointmentVO existing = new AppointmentVO();
        existing.setId(66);
        existing.setPatientId(PATIENT);
        existing.setScheduleId(31);
        existing.setStatus(0);
        when(appointmentMapper.getById(66)).thenReturn(existing);
        LocalThreadHolder.setUserId(PATIENT, 2);

        Result<Void> result = appointmentService.cancelAppointment(66, "临时有事");

        assertEquals(200, result.getCode());
        verify(userNotifier, never()).send(any(), any(), any(), any(), any(), any());
    }

    /** 挂号成功路径的公共 stub */
    private void prepareBooking() {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(31);
        schedule.setDoctorId(DOCTOR);
        schedule.setScheduleDate(LocalDate.of(2026, 10, 20));
        schedule.setTimeSlot("morning");
        when(scheduleMapper.getById(31)).thenReturn(schedule);

        DoctorVO doctor = new DoctorVO();
        doctor.setId(DOCTOR);
        doctor.setName("李医生");
        doctor.setDepartmentId(7);
        when(doctorMapper.getById(DOCTOR)).thenReturn(doctor);

        Department department = new Department();
        department.setId(7);
        department.setName("内科");
        when(departmentMapper.getById(7)).thenReturn(department);

        when(scheduleMapper.reserveSlot(31)).thenReturn(1);
        when(appointmentMapper.countByScheduleId(31)).thenReturn(2);
        // useGeneratedKeys 的回填效果：让 save 顺手写回主键
        doAnswer(inv -> {
            ((Appointment) inv.getArgument(0)).setId(900);
            return null;
        }).when(appointmentMapper).save(any());
    }

    // ================================================================ 商城

    @Test
    @DisplayName("下单成功：给下单人发「订单待付款」通知")
    void orderCreationShouldNotifyBuyer() {
        ShoppingCartVO item = new ShoppingCartVO();
        item.setId(5);
        item.setProductId(11);
        item.setProductName("维生素C");
        item.setQuantity(2);
        item.setProductPrice(new BigDecimal("19.90"));
        when(cartMapper.queryByUserId(PATIENT)).thenReturn(List.of(item));
        when(productMapper.decrementStock(11, 2)).thenReturn(1);
        doAnswer(inv -> {
            ((MallOrder) inv.getArgument(0)).setId(501);
            return null;
        }).when(orderMapper).save(any());

        Result<MallOrderVO> result = mallService.createOrder(PATIENT, null, "尽快发货");

        assertEquals(200, result.getCode());
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(userNotifier).send(eq(PATIENT), eq(NotificationSource.ORDER),
                eq(NotifyTemplates.T_ORDER_CREATED), content.capture(),
                eq(501L), eq(NotifyTemplates.LINK_MALL));
        assertTrue(content.getValue().contains("39.80"), "正文带应付金额: " + content.getValue());
    }

    @Test
    @DisplayName("下单中途失败（购物车为空）：不发通知，因为没有订单")
    void failedOrderCreationShouldNotNotify() {
        // 用「购物车为空」而不是「库存不足」来构造失败：
        // 库存不足分支会调 TransactionAspectSupport.currentTransactionStatus()，
        // 它要求存在真实事务，纯单元测试里会抛 NoTransactionException，
        // 测的就不再是「不通知」而是 Spring 事务基础设施了。
        when(cartMapper.queryByUserId(PATIENT)).thenReturn(List.of());

        Result<MallOrderVO> result = mallService.createOrder(PATIENT, null, null);

        assertNotEquals(200, result.getCode());
        verify(userNotifier, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("支付成功：通知发给订单主人，不是当前操作人（管理员可能代付）")
    void paymentShouldNotifyOrderOwner() {
        MallOrderVO order = new MallOrderVO();
        order.setId(501);
        order.setOrderNo("PH20261010001");
        order.setUserId(PATIENT);
        order.setStatus(0);
        order.setActualAmount(new BigDecimal("39.80"));
        when(orderMapper.getById(501)).thenReturn(order);
        when(orderItemMapper.queryByOrderId(501)).thenReturn(List.of());
        LocalThreadHolder.setUserId(ADMIN, 1);

        Result<Void> result = mallService.payOrder(501, "在线支付");

        assertEquals(200, result.getCode());
        verify(userNotifier).send(eq(PATIENT), eq(NotificationSource.ORDER),
                eq(NotifyTemplates.T_ORDER_PAID), anyString(),
                eq(501L), eq(NotifyTemplates.LINK_MALL));
    }

    @Test
    @DisplayName("管理员发货：通知买家，且走幂等发送（连点两下不重复）")
    void adminShippingShouldNotifyBuyerOnce() {
        MallOrderVO order = new MallOrderVO();
        order.setId(502);
        order.setOrderNo("PH20261010002");
        order.setUserId(PATIENT);
        order.setStatus(1);
        when(orderMapper.getById(502)).thenReturn(order);

        MallOrder patch = new MallOrder();
        patch.setId(502);
        patch.setStatus(2);
        Result<Void> result = mallService.updateOrderStatus(patch);

        assertEquals(200, result.getCode());
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(userNotifier).sendOnce(eq(PATIENT), eq(NotificationSource.ORDER),
                title.capture(), anyString(), eq(502L), eq(NotifyTemplates.LINK_MALL));
        assertEquals("订单已发货", title.getValue());
    }

    @Test
    @DisplayName("管理员把状态改回「待付款/待发货」：不通知（那两步各有自己的即时通知）")
    void nonTransitionStatusShouldNotNotify() {
        MallOrder patch = new MallOrder();
        patch.setId(503);
        patch.setStatus(1);

        mallService.updateOrderStatus(patch);

        verify(userNotifier, never()).sendOnce(any(), any(), any(), any(), any(), any());
        verify(userNotifier, never()).send(any(), any(), any(), any(), any(), any());
        // 状态本身照旧写库，通知失败/跳过不影响流转
        verify(orderMapper).update(patch);
    }

    @Test
    @DisplayName("订单查不到：状态照旧写、通知跳过，不抛异常")
    void missingOrderShouldNotBreakStatusUpdate() {
        when(orderMapper.getById(504)).thenReturn(null);
        MallOrder patch = new MallOrder();
        patch.setId(504);
        patch.setStatus(3);

        Result<Void> result = mallService.updateOrderStatus(patch);

        assertEquals(200, result.getCode());
        verify(userNotifier, never()).sendOnce(any(), any(), any(), any(), any(), any());
    }
}

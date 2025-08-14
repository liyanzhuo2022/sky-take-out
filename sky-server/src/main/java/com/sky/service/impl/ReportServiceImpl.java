package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 根据时间区间统计营业额
     * @param begin
     * @param end
     * @return
     */
    public TurnoverReportVO getTurnover(LocalDate begin, LocalDate end) {
        // 参数校验，避免无效或过大区间
        if (begin == null || end == null) {
            throw new IllegalArgumentException("begin and end dates cannot be null");
        }
        if (end.isBefore(begin)) {
            throw new IllegalArgumentException("end date must be on or after begin date");
        }
        long days = ChronoUnit.DAYS.between(begin, end) + 1;
        if (days > 370) { // 例如限制最大一年
            throw new IllegalArgumentException("Date range too large: " + days + " days");
        }

        // 生成日期列表
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
            dateList.add(d);
        }

        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("status", Orders.COMPLETED);
            map.put("begin",beginTime);
            map.put("end", endTime);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //数据封装
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }

    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        // 1) 参数与范围校验
        if (begin == null || end == null) {
            throw new IllegalArgumentException("begin and end dates cannot be null");
        }
        if (end.isBefore(begin)) {
            throw new IllegalArgumentException("end date must be on or after begin date");
        }
        long days = ChronoUnit.DAYS.between(begin, end) + 1;
        if (days > 370) { // 保护：最多一年（按需调整）
            throw new IllegalArgumentException("Date range too large: " + days + " days");
        }

        // 2) 预分配容量，减少扩容拷贝
        List<String> dateListStr = new ArrayList<>((int) days);
        List<Integer> newUserList = new ArrayList<>((int) days);   // 新增用户数
        List<Integer> totalUserList = new ArrayList<>((int) days); // 累计用户数

        // 3) 循环每日：使用 [dayStart, nextDayStart) 作为统计窗口
        for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
            LocalDateTime dayStart = d.atStartOfDay();           // 00:00:00
            LocalDateTime nextDayStart = d.plusDays(1).atStartOfDay(); // 次日 00:00:00

            // 新增用户：create_time >= dayStart AND create_time < nextDayStart
            int newUsers = getUserCount(dayStart, nextDayStart);

            // 总用户数：create_time < nextDayStart（统计到当天结束）
            int totalUsers = getUserCount(null, nextDayStart);

            dateListStr.add(d.toString()); // yyyy-MM-dd
            newUserList.add(newUsers);
            totalUserList.add(totalUsers);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateListStr, ","))         // "2025-08-01,2025-08-02,..."
                .newUserList(StringUtils.join(newUserList, ","))      // "3,5,2,..."
                .totalUserList(StringUtils.join(totalUserList, ","))  // "10,15,17,..."
                .build();
    }

    /**
     * 根据时间区间统计用户数量
     * @param beginTime
     * @param endTime
     * @return
     */
    private Integer getUserCount(LocalDateTime beginTime, LocalDateTime endTime) {
        Map map = new HashMap();
        map.put("begin",beginTime);
        map.put("end", endTime);
        return userMapper.countByMap(map);
    }

    /**
     * 根据时间区间统计订单数量
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end){
        // 参数校验，避免无效或过大区间
        if (begin == null || end == null) {
            throw new IllegalArgumentException("begin and end dates cannot be null");
        }
        if (end.isBefore(begin)) {
            throw new IllegalArgumentException("end date must be on or after begin date");
        }
        long days = ChronoUnit.DAYS.between(begin, end) + 1;
        if (days > 370) { // 例如限制最大一年
            throw new IllegalArgumentException("Date range too large: " + days + " days");
        }

        // 生成日期列表
        List<LocalDate> dateList = new ArrayList<>();
        for (LocalDate d = begin; !d.isAfter(end); d = d.plusDays(1)) {
            dateList.add(d);
        }

        //每天订单总数集合
        List<Integer> orderCountList = new ArrayList<>();
        //每天有效订单数集合
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //查询每天的总订单数 select count(id) from orders where order_time > ? and order_time < ?
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            //查询每天的有效订单数 select count(id) from orders where order_time > ? and order_time < ? and status = ?
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //时间区间内的总订单数
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //时间区间内的总有效订单数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();
        //订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0){
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }
        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 根据时间区间统计指定状态的订单数量
     * @param beginTime
     * @param endTime
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {
        Map map = new HashMap();
        map.put("status", status);
        map.put("begin",beginTime);
        map.put("end", endTime);
        return orderMapper.countByMap(map);
    }

    /**
     * 查询指定时间区间内的销量排名top10
     * @param begin
     * @param end
     * @return
     * */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end){
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginTime, endTime);

        String nameList = StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()),",");
        String numberList = StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()),",");

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }



}

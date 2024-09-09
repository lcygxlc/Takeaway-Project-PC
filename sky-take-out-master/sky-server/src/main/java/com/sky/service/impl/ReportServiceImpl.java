package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 统计指定时间区间的营业额
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //计算从开始到结束日期的dateList
        List<LocalDate> dateList = getDateList(begin, end);

        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //获取日期开始结束具体时间
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //查询指定时间区间的订单营业额
            Map map = new HashMap() {{
                put("beginTime", beginTime);
                put("endTime", endTime);
                put("status", Orders.COMPLETED);
            }};
            Double turnover = orderMapper.sumByMap(map);
            //如果营业额为空，设置为0
            turnover = turnover == null ? 0 : turnover;
            turnoverList.add(turnover);
        }
        //将turnoverList和dateList转成字符串封装进VO
        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
            .dateList(StringUtils.join(dateList, ","))
            .turnoverList(StringUtils.join(turnoverList, ","))
            .build();
        return turnoverReportVO;
    }

    /**
     * 统计指定时间区间的用户注册量
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //计算从开始到结束日期的dateList
        List<LocalDate> dateList = getDateList(begin, end);

        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //获取日期开始结束具体时间
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
           //查询指定时间区间的总用户注册量
            Map map = new HashMap() {{
                put("endTime", endTime);
            }};
            Integer totalUserCount = userMapper.countByMap(map);
            //查询指定时间区间的新用户注册量,即注册时间在指定时间区间内的用户数量
            map.put("beginTime", beginTime);
            Integer newUserCount = userMapper.countByMap(map);
            //如果用户数量为空，设置为0
            totalUserCount = totalUserCount == null ? 0 : totalUserCount;
            newUserCount = newUserCount == null ? 0 : newUserCount;
            totalUserList.add(totalUserCount);
            newUserList.add(newUserCount);
        }
        //将totalUserList、newUserList和dateList转成字符串封装进VO
        UserReportVO userReportVO = UserReportVO.builder()
            .dateList(StringUtils.join(dateList, ","))
            .totalUserList(StringUtils.join(totalUserList, ","))
            .newUserList(StringUtils.join(newUserList, ","))
            .build();
        return userReportVO;
    }

    /**
     * 统计指定时间区间的订单量
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getDateList(begin, end);
        //遍历dateList，查询每天的订单量,每天的有效订单量,订单总数,有效订单总数
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();

        for (LocalDate date : dateList) {
            //获取日期开始结束具体时间
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //查询指定时间区间的订单量
            Map map = new HashMap() {{
                put("beginTime", beginTime);
                put("endTime", endTime);
            }};
            Integer orderCount = getOrderCount(map);
            //查询指定时间区间的有效订单量
            map.put("status", Orders.COMPLETED);
            Integer validOrderCount = getOrderCount(map);
            //存放每天的订单量和有效订单量
            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);

        }
        //计算总订单量和有效订单量
        Integer totalOrderCountList = orderCountList.stream().mapToInt(Integer::intValue).sum();
        Integer totalValidOrderCountList = validOrderCountList.stream().mapToInt(Integer::intValue).sum();
        //计算总订单量和有效订单量的占比,前端自动乘以100,不用另计算
        Double orderCompletionRate = (totalOrderCountList == 0 ? 0.0 : totalValidOrderCountList.doubleValue()/ totalOrderCountList);
        //将orderCountList、validOrderCountList和dateList,orderCompletionRate转成字符串封装进VO
        OrderReportVO orderReportVO = OrderReportVO.builder()
            .dateList(StringUtils.join(dateList, ","))
            .orderCountList(StringUtils.join(orderCountList, ","))
            .validOrderCountList(StringUtils.join(validOrderCountList, ","))
            .totalOrderCount(totalOrderCountList)
            .validOrderCount(totalValidOrderCountList)
            .orderCompletionRate(orderCompletionRate.doubleValue())
            .build();
        return orderReportVO;
    }

    /**
     * 统计指定时间区间的商品销量top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = getDateList(begin, end);
        //查询商品销量
        //select od.name,sum(od.number) from order_detail od,orders o where od.order_id = o.id and o.status = 5 and o.order_time >= beginTime and o.order_time <= endTime group by od.name order by sum(od.number) desc limit 10
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> goodsSalesDTOList = orderMapper.getSalesTop10(beginTime, endTime);
        //将goodsSalesDTOList中的name和number分别转成列表字符串封装进VO
        SalesTop10ReportVO salesTop10ReportVO = SalesTop10ReportVO.builder()
            .nameList(StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()), ","))
            .numberList(StringUtils.join(goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()), ","))
            .build();
        return salesTop10ReportVO;
    }

    /**
     * 导出营业数据报表
     *
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        /*查询近30天的营业数据
        * 概览数据:
        *   通过workspaceService的getBusinessData接口即可*/
        LocalDate beginDate = LocalDate.now().minusDays(30);
        LocalDate endDate= LocalDate.now().minusDays(1);

        LocalDateTime beginTime = LocalDateTime.of(beginDate, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(endDate, LocalTime.MAX);

        //查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(beginTime, endTime);

        //基于现有的模板文件创建一个新的excel文件,并写入数据
        try {
            XSSFWorkbook excel = new XSSFWorkbook(this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx"));
            XSSFSheet sheet = excel.getSheet("Sheet1");
            //填充时间数据
            sheet.getRow(1).getCell(1).setCellValue("时间: " + beginDate + " 至 " + endDate);
            //填充第4行
            XSSFRow row = sheet.getRow(3);
            //填充营业额
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            //填充订单完成率
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            //填充新增用户数
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());
            //填充第5行
            row = sheet.getRow(4);
            //填充有效订单数
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            //填充平均客单价
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //填充明细数据
            List<LocalDate> dateList = getDateList(beginDate, endDate);
            for (int i = 0; i < dateList.size(); i++) {
                LocalDate date = dateList.get(i);
                //获取日期开始结束具体时间
                beginTime = LocalDateTime.of(date, LocalTime.MIN);
                endTime = LocalDateTime.of(date, LocalTime.MAX);
                //查询指定时间区间的营业数据
                BusinessDataVO businessData = workspaceService.getBusinessData(beginTime, endTime);
                //填充某一行数据
                row = sheet.getRow(7 + i);
                //填充日期
                row.getCell(1).setCellValue(date.toString());
                //填充营业额
                row.getCell(2).setCellValue(businessData.getTurnover());
                //填充有效订单数
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                //填充订单完成率
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                //填充平均客单价
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                //填充新增用户数
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }




            //通过输出流将excel文件输出到浏览器
            ServletOutputStream outputStream = response.getOutputStream();
            excel.write(outputStream);
            outputStream.flush();
            //关闭资源
            outputStream.close();
            excel.close();


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static List<LocalDate> getDateList(LocalDate begin, LocalDate end) {
        return Stream.iterate(begin, date -> date.plusDays(1))
            .limit(ChronoUnit.DAYS.between(begin, end.plusDays(1)))
            .collect(Collectors.toList());
    }

    private Integer getOrderCount(Map map){
        Integer orderCount = orderMapper.countByMap(map);
        orderCount = orderCount == null ? 0 : orderCount;
        return orderCount;
    }
}

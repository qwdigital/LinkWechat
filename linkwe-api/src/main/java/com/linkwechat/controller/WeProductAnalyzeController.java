package com.linkwechat.controller;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkwechat.common.annotation.Log;
import com.linkwechat.common.constant.ProductOrderConstants;
import com.linkwechat.common.core.controller.BaseController;
import com.linkwechat.common.core.domain.AjaxResult;
import com.linkwechat.common.core.page.TableDataInfo;
import com.linkwechat.common.enums.BusinessType;
import com.linkwechat.common.utils.ServletUtils;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.domain.WeProductDayStatistics;
import com.linkwechat.domain.WeProductStatistics;
import com.linkwechat.domain.product.analyze.vo.WeProductAnalyzeStatisticsVo;
import com.linkwechat.domain.product.analyze.vo.WeProductOrderDataReportVo;
import com.linkwechat.domain.product.analyze.vo.WeProductOrderTop5Vo;
import com.linkwechat.domain.product.order.query.WeProductOrderQuery;
import com.linkwechat.domain.product.order.vo.WeProductOrderWareVo;
import com.linkwechat.domain.product.product.query.WeProductLineChartQuery;
import com.linkwechat.domain.product.product.vo.WeUserOrderTop5Vo;
import com.linkwechat.domain.product.refund.vo.WeProductOrderRefundVo;
import com.linkwechat.service.IWeProductDayStatisticsService;
import com.linkwechat.service.IWeProductOrderService;
import com.linkwechat.service.IWeProductStatisticsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * ????????????
 *
 * @author WangYX
 * @version 1.0.0
 * @date 2022/11/22 15:44
 */
@Slf4j
@Api(tags = "????????????")
@RestController
@RequestMapping("/product/analyze")
public class WeProductAnalyzeController extends BaseController {

    @Resource
    private IWeProductStatisticsService weProductStatisticsService;
    @Resource
    private IWeProductDayStatisticsService weProductDayStatisticsService;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private IWeProductOrderService weProductOrderService;

    @ApiOperation(value = "??????????????????", httpMethod = "GET")
    @Log(title = "??????????????????", businessType = BusinessType.SELECT)
    @GetMapping("/statistics")
    public AjaxResult<WeProductAnalyzeStatisticsVo> statistics() {
        //????????????
        WeProductAnalyzeStatisticsVo weProductAnalyzeStatisticsVo = new WeProductAnalyzeStatisticsVo();
        //???????????????
        //????????????
        weProductAnalyzeStatisticsVo.setOrderNum(0);
        weProductAnalyzeStatisticsVo.setTotalFee(BigDecimal.ZERO);
        weProductAnalyzeStatisticsVo.setRefundFee(BigDecimal.ZERO);
        weProductAnalyzeStatisticsVo.setNetIncome(BigDecimal.ZERO);
        //???????????????
        weProductAnalyzeStatisticsVo.setTodayOrderNum(0);
        weProductAnalyzeStatisticsVo.setTodayTotalFee(BigDecimal.ZERO);
        weProductAnalyzeStatisticsVo.setTodayRefundFee(BigDecimal.ZERO);
        weProductAnalyzeStatisticsVo.setTodayNetIncome(BigDecimal.ZERO);
        //????????????????????????
        weProductAnalyzeStatisticsVo.setOrderNumComparedToYes(0);
        weProductAnalyzeStatisticsVo.setTotalFeeComparedToYes(BigDecimal.ZERO);
        weProductAnalyzeStatisticsVo.setRefundFeeComparedToYes(BigDecimal.ZERO);
        weProductAnalyzeStatisticsVo.setNetIncomeComparedToYes(BigDecimal.ZERO);

        //?????????????????????=>??????????????????
        LambdaQueryWrapper<WeProductStatistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WeProductStatistics::getDelFlag, 0);
        List<WeProductStatistics> list = weProductStatisticsService.list(queryWrapper);
        if (list != null && list.size() > 0) {
            WeProductStatistics weProductStatistics = list.get(0);
            weProductAnalyzeStatisticsVo.setOrderNum(weProductStatistics.getOrderTotalNum());
            weProductAnalyzeStatisticsVo.setTotalFee(new BigDecimal(weProductStatistics.getOrderTotalFee()));
            weProductAnalyzeStatisticsVo.setRefundFee(new BigDecimal(weProductStatistics.getRefundTotalFee()));
            weProductAnalyzeStatisticsVo.setNetIncome(new BigDecimal(weProductStatistics.getNetIncome()));
        }

        //???Redis????????????????????????
        //??????????????????
        Integer todayOrderNum = (Integer) redisTemplate.opsForValue().get(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_NUMBER);
        //????????????????????????
        String todayTotalFeeStr = (String) redisTemplate.opsForValue().get(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_TOTAL_FEE);
        //????????????????????????
        String todayRefundFeeStr = (String) redisTemplate.opsForValue().get(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_REFUND_FEE);

        todayOrderNum = todayOrderNum == null ? 0 : todayOrderNum;
        todayTotalFeeStr = StringUtils.isNotBlank(todayTotalFeeStr) ? todayTotalFeeStr : "0";
        todayRefundFeeStr = StringUtils.isNotBlank(todayRefundFeeStr) ? todayRefundFeeStr : "0";

        BigDecimal tempBigDecimal = BigDecimal.valueOf(100L);
        //????????????????????????
        weProductAnalyzeStatisticsVo.setTodayOrderNum(todayOrderNum);
        weProductAnalyzeStatisticsVo.setOrderNum(weProductAnalyzeStatisticsVo.getOrderNum() + todayOrderNum);
        //????????????????????????
        BigDecimal todayTotalFee = new BigDecimal(todayTotalFeeStr);
        weProductAnalyzeStatisticsVo.setTodayTotalFee(todayTotalFee.divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
        weProductAnalyzeStatisticsVo.setTotalFee(weProductAnalyzeStatisticsVo.getTotalFee().add(todayTotalFee).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
        //????????????????????????
        BigDecimal todayRefundFee = new BigDecimal(todayRefundFeeStr);
        weProductAnalyzeStatisticsVo.setTodayRefundFee(todayRefundFee.divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
        weProductAnalyzeStatisticsVo.setRefundFee(weProductAnalyzeStatisticsVo.getRefundFee().add(todayRefundFee).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
        //?????????????????????
        BigDecimal subtract = todayTotalFee.subtract(todayRefundFee);
        weProductAnalyzeStatisticsVo.setTodayNetIncome(subtract.divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
        weProductAnalyzeStatisticsVo.setNetIncome(weProductAnalyzeStatisticsVo.getNetIncome().add(subtract).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));

        //?????????????????????
        DateTime dateTime = DateUtil.offsetDay(new Date(), -1);
        LambdaQueryWrapper<WeProductDayStatistics> query = new LambdaQueryWrapper<>();
        query.eq(WeProductDayStatistics::getDelFlag, 0);
        query.apply("date_format(create_time,'yyyy-MM-dd') = date_format('" + dateTime + "','yyyy-MM-dd')");
        WeProductDayStatistics yesterdayData = weProductDayStatisticsService.getOne(query);
        if (ObjectUtil.isEmpty(yesterdayData)) {
            yesterdayData = new WeProductDayStatistics();
            yesterdayData.setDayOrderTotalNum(0);
            yesterdayData.setDayRefundTotalFee("0");
            yesterdayData.setDayOrderTotalFee("0");
            yesterdayData.setDayNetIncome("0");
        }
        weProductAnalyzeStatisticsVo.setOrderNumComparedToYes(weProductAnalyzeStatisticsVo.getTodayOrderNum() - yesterdayData.getDayOrderTotalNum());
        weProductAnalyzeStatisticsVo.setTotalFeeComparedToYes(weProductAnalyzeStatisticsVo.getTodayTotalFee().subtract(new BigDecimal(yesterdayData.getDayOrderTotalFee()).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)));
        weProductAnalyzeStatisticsVo.setRefundFeeComparedToYes(weProductAnalyzeStatisticsVo.getTodayRefundFee().subtract(new BigDecimal(yesterdayData.getDayRefundTotalFee()).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)));
        weProductAnalyzeStatisticsVo.setNetIncomeComparedToYes(weProductAnalyzeStatisticsVo.getTodayNetIncome().subtract(new BigDecimal(yesterdayData.getDayNetIncome()).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP)));

        return AjaxResult.success(weProductAnalyzeStatisticsVo);
    }

    @ApiOperation(value = "?????????????????????", httpMethod = "POST")
    @Log(title = "?????????????????????", businessType = BusinessType.SELECT)
    @PostMapping("/lineChart")
    public AjaxResult lineChart(@RequestBody WeProductLineChartQuery query) {
        String type = query.getType();
        Date startTime = null;
        Date endTime = null;
        if (!type.equals("customization")) {
            if (type.equals("week")) {
                startTime = DateUtil.offsetWeek(new Date(), -1);
            } else if (type.equals("month")) {
                startTime = DateUtil.offsetMonth(new Date(), -1);
            }
            endTime = new Date();
        } else {
            startTime = DateUtil.parse(query.getStartDate(), "yyyy-MM-dd");
            endTime = DateUtil.parse(query.getEndDate(), "yyyy-MM-dd");
        }

        List<DateTime> timeList = DateUtil.rangeToList(startTime, endTime, DateField.DAY_OF_YEAR);

        //?????????X?????????
        String[] xAxisArray = new String[timeList.size()];
        //????????????(???)
        String[] totalFeeArray = new String[timeList.size()];
        //????????????(???)
        String[] refundFeeArray = new String[timeList.size()];
        //?????????
        String[] netIncomeArray = new String[timeList.size()];

        //?????????????????????????????????
        LambdaQueryWrapper<WeProductDayStatistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(WeProductDayStatistics::getCreateTime, startTime, endTime);
        List<WeProductDayStatistics> list = weProductDayStatisticsService.list(queryWrapper);

        for (int i = 0; i < timeList.size(); i++) {
            String dateStr = timeList.get(i).toDateStr();
            xAxisArray[i] = dateStr;
            Optional<WeProductDayStatistics> first = list.stream().filter(o -> DateUtil.format(o.getCreateTime(), "yyyy-MM-dd").equals(dateStr)).findFirst();
            if (first.isPresent()) {
                WeProductDayStatistics weProductDayStatistics = first.get();
                BigDecimal totalFee = new BigDecimal(weProductDayStatistics.getDayOrderTotalFee()).divide(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                BigDecimal refundFee = new BigDecimal(weProductDayStatistics.getDayRefundTotalFee()).divide(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                totalFeeArray[i] = totalFee.toString();
                refundFeeArray[i] = refundFee.toString();
                netIncomeArray[i] = totalFee.subtract(totalFee).toString();
            } else {
                totalFeeArray[i] = "0";
                refundFeeArray[i] = "0";
                netIncomeArray[i] = "0";
            }
        }

        Map<String, Object> totalFeeData = new HashMap<>();
        totalFeeData.put("data", totalFeeArray);
        totalFeeData.put("name", "?????????????????????");

        Map<String, Object> refundFeeData = new HashMap<>();
        refundFeeData.put("data", refundFeeArray);
        refundFeeData.put("name", "?????????????????????");

        Map<String, Object> netIncomeData = new HashMap<>();
        netIncomeData.put("data", netIncomeArray);
        netIncomeData.put("name", "??????????????????");

        Map<String, Object> xAxisData = new HashMap<>();
        xAxisData.put("data", xAxisArray);
        Map<String, Map<String, Object>> xAxisDataMap = new HashMap<>();
        xAxisDataMap.put("xAxis", xAxisData);

        List series = new ArrayList();
        series.add(totalFeeData);
        series.add(refundFeeData);
        series.add(netIncomeData);

        Map<String, Object> seriesMap = new HashMap<>();
        seriesMap.put("series", list);

        Map<String, Object> legendData = new HashMap<>();
        String[] legendArray = new String[]{"?????????????????????", "?????????????????????", "??????????????????"};
        legendData.put("data", legendArray);

        Map map = new HashMap();
        map.put("legend", legendData);
        map.put("xAxis", xAxisData);
        map.put("series", series);
        return AjaxResult.success(map);
    }

    @ApiOperation(value = "????????????Top5", httpMethod = "POST")
    @Log(title = "????????????Top5", businessType = BusinessType.SELECT)
    @PostMapping("/top5")
    public AjaxResult<List<WeProductOrderTop5Vo>> productOrderTop5(@RequestBody WeProductLineChartQuery query) {

        //????????????
        String type = query.getType();
        Date startTime = null;
        Date endTime = null;
        if (!type.equals("customization")) {
            if (type.equals("week")) {
                startTime = DateUtil.offsetWeek(new Date(), -1);
            } else if (type.equals("month")) {
                startTime = DateUtil.offsetMonth(new Date(), -1);
            }
            endTime = new Date();
        } else {
            startTime = DateUtil.parse(query.getStartDate(), "yyyy-MM-dd");
            endTime = DateUtil.parse(query.getEndDate(), "yyyy-MM-dd");
        }

        //???????????????????????????
        WeProductOrderQuery weProductOrderQuery = new WeProductOrderQuery();
        weProductOrderQuery.setBeginTime(DateUtil.format(startTime, "yyyy-MM-dd"));
        weProductOrderQuery.setEndTime(DateUtil.format(endTime, "yyyy-MM-dd"));
        List<WeProductOrderWareVo> list = weProductOrderService.list(weProductOrderQuery);
        Map<Long, List<WeProductOrderWareVo>> collect = list.stream().collect(Collectors.groupingBy(WeProductOrderWareVo::getProductId));

        //????????????
        List<WeProductOrderTop5Vo> result = new ArrayList<>();

        //????????????
        Iterator<Map.Entry<Long, List<WeProductOrderWareVo>>> iterator = collect.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, List<WeProductOrderWareVo>> next = iterator.next();

            WeProductOrderTop5Vo weProductOrderTop5Vo = new WeProductOrderTop5Vo();
            weProductOrderTop5Vo.setProductId(next.getKey());

            List<WeProductOrderWareVo> value = next.getValue();
            weProductOrderTop5Vo.setProductDesc(value.get(0).getDescribe());
            weProductOrderTop5Vo.setPicture(value.get(0).getPicture());
            weProductOrderTop5Vo.setOrderNum(value.size());
            double totalFee = value.stream().flatMapToDouble(o -> DoubleStream.of(Double.valueOf(o.getTotalFee()))).sum();
            final BigDecimal totalFeeDec = BigDecimal.valueOf(totalFee).divide(BigDecimal.valueOf(100L).setScale(2, BigDecimal.ROUND_HALF_UP));
            weProductOrderTop5Vo.setTotalFee(totalFeeDec);

            List<WeProductOrderRefundVo> refundVos = value.stream().flatMap(o -> o.getRefunds().stream()).collect(Collectors.toList());
            double refundFee = refundVos.stream().filter(o -> o.getRefundState().equals(2)).flatMapToDouble(o -> DoubleStream.of(Double.valueOf(o.getRefundFee()))).sum();
            BigDecimal refundFeeDec = BigDecimal.valueOf(refundFee).divide(BigDecimal.valueOf(100L).setScale(2, BigDecimal.ROUND_HALF_UP));
            weProductOrderTop5Vo.setNetIncome(totalFeeDec.subtract(refundFeeDec));
            result.add(weProductOrderTop5Vo);
        }
        result.sort(Comparator.comparing(WeProductOrderTop5Vo::getTotalFee).reversed());
        if (result.size() > 5) {
            result = result.subList(0, 5);
        }
        return AjaxResult.success(result);
    }

    @ApiOperation(value = "????????????Top5", httpMethod = "POST")
    @Log(title = "????????????Top5", businessType = BusinessType.SELECT)
    @PostMapping("/user/top5")
    public AjaxResult<List<WeUserOrderTop5Vo>> userOrderTop5(@RequestBody WeProductLineChartQuery query) {
        //????????????
        String type = query.getType();
        Date startTime = null;
        Date endTime = null;
        if (!type.equals("customization")) {
            if (type.equals("week")) {
                startTime = DateUtil.offsetWeek(new Date(), -1);
            } else if (type.equals("month")) {
                startTime = DateUtil.offsetMonth(new Date(), -1);
            }
            endTime = new Date();
        } else {
            startTime = DateUtil.parse(query.getStartDate(), "yyyy-MM-dd");
            endTime = DateUtil.parse(query.getEndDate(), "yyyy-MM-dd");
        }

        //????????????
        List<WeUserOrderTop5Vo> result = new ArrayList<>();

        //???????????????????????????
        WeProductOrderQuery weProductOrderQuery = new WeProductOrderQuery();
        weProductOrderQuery.setBeginTime(DateUtil.format(startTime, "yyyy-MM-dd"));
        weProductOrderQuery.setEndTime(DateUtil.format(endTime, "yyyy-MM-dd"));
        List<WeProductOrderWareVo> list = weProductOrderService.list(weProductOrderQuery);
        if (list != null && list.size() > 0) {
            Map<String, List<WeProductOrderWareVo>> collect = list.stream().collect(Collectors.groupingBy(WeProductOrderWareVo::getWeUserId));
            Iterator<Map.Entry<String, List<WeProductOrderWareVo>>> iterator = collect.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, List<WeProductOrderWareVo>> next = iterator.next();
                //??????????????????
                WeUserOrderTop5Vo weUserOrderTop5Vo = new WeUserOrderTop5Vo();
                weUserOrderTop5Vo.setWeUserId(next.getKey());
                weUserOrderTop5Vo.setWeUserName(next.getValue().get(0).getWeUserName());
                weUserOrderTop5Vo.setOrderNum(next.getValue().size());
                double sum = next.getValue().stream().flatMapToDouble(o -> DoubleStream.of(Double.valueOf(o.getTotalFee()))).sum();
                weUserOrderTop5Vo.setTotalFee(BigDecimal.valueOf(sum).toString());
                result.add(weUserOrderTop5Vo);
            }
        }
        result.sort(Comparator.comparing(WeUserOrderTop5Vo::getTotalFee).reversed());
        if (result.size() > 5) {
            result.subList(0, 5);
        }
        return AjaxResult.success(result);
    }

    @ApiOperation(value = "????????????-??????", httpMethod = "GET")
    @Log(title = "????????????-??????", businessType = BusinessType.SELECT)
    @GetMapping("/data/report")
    public TableDataInfo<WeProductOrderDataReportVo> dataReport(WeProductLineChartQuery query) {
        startPage();
        List<WeProductOrderDataReportVo> result = getProductOrderDataReportVos(query);
        return getDataTable(result);
    }

    /**
     * ??????????????????
     *
     * @author WangYX
     * @date 2022/11/23 11:11
     * @version 1.0.0
     */
    private List<WeProductOrderDataReportVo> getProductOrderDataReportVos(WeProductLineChartQuery query) {
        //????????????
        String type = query.getType();
        Date startTime = null;
        Date endTime = null;
        if (!type.equals("customization")) {
            if (type.equals("week")) {
                startTime = DateUtil.offsetWeek(new Date(), -1);
            } else if (type.equals("month")) {
                startTime = DateUtil.offsetMonth(new Date(), -1);
            }
            endTime = new Date();
        } else {
            startTime = DateUtil.parse(query.getStartDate(), "yyyy-MM-dd");
            endTime = DateUtil.parse(query.getEndDate(), "yyyy-MM-dd");
        }
        LambdaQueryWrapper<WeProductDayStatistics> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.between(WeProductDayStatistics::getCreateTime, startTime, endTime);
        List<WeProductDayStatistics> list = weProductDayStatisticsService.list(queryWrapper);

        BigDecimal tempBigDecimal = BigDecimal.valueOf(100L);
        //????????????
        List<WeProductOrderDataReportVo> result = new ArrayList<>();
        if (list != null && list.size() > 0) {
            list.sort(Comparator.comparing(WeProductDayStatistics::getCreateTime).reversed());
            for (WeProductDayStatistics weProductDayStatistics : list) {
                WeProductOrderDataReportVo weProductOrderDataReportVo = new WeProductOrderDataReportVo();
                String format = DateUtil.format(weProductDayStatistics.getCreateTime(), "yyyy-MM-dd");
                weProductOrderDataReportVo.setDateStr(format);
                weProductOrderDataReportVo.setTotalFee(new BigDecimal(weProductDayStatistics.getDayOrderTotalFee()).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
                weProductOrderDataReportVo.setRefundFee(new BigDecimal(weProductDayStatistics.getDayRefundTotalFee()).divide(tempBigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP));
                weProductOrderDataReportVo.setNetIncome(weProductOrderDataReportVo.getTotalFee().subtract(weProductOrderDataReportVo.getRefundFee()));
                weProductOrderDataReportVo.setOrderNum(weProductDayStatistics.getDayOrderTotalNum());
                result.add(weProductOrderDataReportVo);
            }
        }
        return result;
    }

    @ApiOperation(value = "??????????????????", httpMethod = "POST")
    @Log(title = "??????????????????", businessType = BusinessType.SELECT)
    @PostMapping("/data/report/export")
    public void dataReportExport(@RequestBody WeProductLineChartQuery query) throws IOException {
        List<WeProductOrderDataReportVo> result = getProductOrderDataReportVos(query);
        HttpServletResponse response = ServletUtils.getResponse();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("????????????????????????", "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), WeProductOrderDataReportVo.class).sheet("????????????????????????").doWrite(result);


    }


    /**
     * ?????????????????????????????????
     * ??????????????????????????????
     *
     * @author WangYX
     * @date 2022/11/22 16:59
     * @version 1.0.0
     */
    @PostConstruct
    public void initProductOrderData() {
        log.info("?????????????????????????????????=>>>>>>>>>>>>>>>>>>>>>>");
        LambdaQueryWrapper<WeProductDayStatistics> query = new LambdaQueryWrapper<>();
        query.select(WeProductDayStatistics::getId, WeProductDayStatistics::getDayOrderTotalNum, WeProductDayStatistics::getDayOrderTotalFee, WeProductDayStatistics::getDayRefundTotalFee);
        query.eq(WeProductDayStatistics::getDelFlag, 0);
        query.apply("date_format(create_time,'%Y-%m-%d') = date_format(now(),'%Y-%m-%d')");
        WeProductDayStatistics weProductDayStatistics = weProductDayStatisticsService.getOne(query);
        if (ObjectUtil.isNotEmpty(weProductDayStatistics)) {
            //??????????????????
            redisTemplate.opsForValue().set(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_NUMBER, weProductDayStatistics.getDayOrderTotalNum());
            //????????????????????????
            redisTemplate.opsForValue().set(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_TOTAL_FEE, weProductDayStatistics.getDayOrderTotalFee());
            //????????????????????????
            redisTemplate.opsForValue().set(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_REFUND_FEE, weProductDayStatistics.getDayRefundTotalFee());
        } else {
            //??????????????????
            Boolean orderNumber = redisTemplate.hasKey(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_NUMBER);
            if (!orderNumber) {
                redisTemplate.opsForValue().set(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_NUMBER, 0);
            }
            //????????????????????????
            Boolean totalFee = redisTemplate.hasKey(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_TOTAL_FEE);
            if (!totalFee) {
                redisTemplate.opsForValue().set(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_TOTAL_FEE, "0");
            }
            //????????????????????????
            Boolean refundFee = redisTemplate.hasKey(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_REFUND_FEE);
            if (!refundFee) {
                redisTemplate.opsForValue().set(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_REFUND_FEE, "0");
            }
        }
    }

    /**
     * ??????????????????????????????
     * ????????????????????????
     *
     * @author WangYX
     * @date 2022/11/22 17:02
     * @version 1.0.0
     */
    @PreDestroy
    public void destroyProductOrderData() {
        log.info("??????????????????????????????");
        //??????????????????
        Integer todayOrderNum = (Integer) redisTemplate.opsForValue().get(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_NUMBER);
        //????????????????????????
        String todayTotalFeeStr = (String) redisTemplate.opsForValue().get(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_TOTAL_FEE);
        //????????????????????????
        String todayRefundFeeStr = (String) redisTemplate.opsForValue().get(ProductOrderConstants.PRODUCT_ANALYZE_ORDER_REFUND_FEE);

        todayOrderNum = todayOrderNum == null ? 0 : todayOrderNum;
        todayTotalFeeStr = StringUtils.isBlank(todayTotalFeeStr) ? "0" : todayTotalFeeStr;
        todayRefundFeeStr = StringUtils.isBlank(todayRefundFeeStr) ? "0" : todayRefundFeeStr;

        LambdaQueryWrapper<WeProductDayStatistics> query = new LambdaQueryWrapper<>();
        query.eq(WeProductDayStatistics::getDelFlag, 0);
        query.apply("date_format(create_time,'%Y-%m-%d') = date_format(now(),'%Y-%m-%d')");
        WeProductDayStatistics weProductDayStatistics = weProductDayStatisticsService.getOne(query);
        if (ObjectUtil.isNotEmpty(weProductDayStatistics)) {
            //????????????
            weProductDayStatistics.setDayOrderTotalNum(weProductDayStatistics.getDayOrderTotalNum() + todayOrderNum);

            BigDecimal dayTotalFee = new BigDecimal(weProductDayStatistics.getDayOrderTotalFee()).add(new BigDecimal(todayTotalFeeStr));
            weProductDayStatistics.setDayOrderTotalFee(dayTotalFee.toString());

            BigDecimal dayRefundFee = new BigDecimal(weProductDayStatistics.getDayRefundTotalFee()).add(new BigDecimal(todayRefundFeeStr));
            weProductDayStatistics.setDayRefundTotalFee(dayRefundFee.toString());

            BigDecimal subtract = new BigDecimal(weProductDayStatistics.getDayOrderTotalFee()).subtract(new BigDecimal(weProductDayStatistics.getDayRefundTotalFee()));
            weProductDayStatistics.setDayNetIncome(subtract.toString());
            weProductDayStatistics.setUpdateTime(LocalDateTime.now());
            weProductDayStatisticsService.updateById(weProductDayStatistics);
        } else {
            //???????????????
            weProductDayStatistics = new WeProductDayStatistics();
            weProductDayStatistics.setDayOrderTotalNum(todayOrderNum);
            weProductDayStatistics.setDayOrderTotalFee(todayTotalFeeStr);
            weProductDayStatistics.setDayRefundTotalFee(todayRefundFeeStr);
            BigDecimal subtract = new BigDecimal(weProductDayStatistics.getDayOrderTotalFee()).subtract(new BigDecimal(weProductDayStatistics.getDayRefundTotalFee()));
            weProductDayStatistics.setDayNetIncome(subtract.toString());
            weProductDayStatistics.setCreateTime(LocalDateTime.now());
            weProductDayStatistics.setUpdateTime(LocalDateTime.now());
            weProductDayStatisticsService.save(weProductDayStatistics);
        }
    }
}

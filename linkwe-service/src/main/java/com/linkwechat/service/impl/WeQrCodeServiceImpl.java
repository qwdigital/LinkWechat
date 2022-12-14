package com.linkwechat.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.linkwechat.common.core.page.PageDomain;
import com.linkwechat.common.core.page.TableSupport;
import com.linkwechat.common.enums.WeErrorCodeEnum;
import com.linkwechat.common.exception.wecom.WeComException;
import com.linkwechat.common.utils.DateUtils;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.common.utils.sql.SqlUtil;
import com.linkwechat.config.rabbitmq.RabbitMQSettingConfig;
import com.linkwechat.domain.WeCorpAccount;
import com.linkwechat.domain.WeCustomer;
import com.linkwechat.domain.corp.query.WeCorpAccountQuery;
import com.linkwechat.domain.corp.vo.WeCorpAccountVo;
import com.linkwechat.domain.qr.WeQrAttachments;
import com.linkwechat.domain.qr.WeQrCode;
import com.linkwechat.domain.qr.query.WeQrAddQuery;
import com.linkwechat.domain.qr.query.WeQrCodeListQuery;
import com.linkwechat.domain.qr.query.WeQrUserInfoQuery;
import com.linkwechat.domain.qr.vo.*;
import com.linkwechat.domain.wecom.query.qr.WeAddWayQuery;
import com.linkwechat.domain.wecom.query.qr.WeContactWayQuery;
import com.linkwechat.domain.wecom.vo.WeResultVo;
import com.linkwechat.domain.wecom.vo.qr.WeAddWayVo;
import com.linkwechat.fegin.QwCustomerClient;
import com.linkwechat.mapper.WeQrCodeMapper;
import com.linkwechat.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ???????????????(WeQrCode)$desc
 *
 * @author danmo
 * @since 2021-11-07 02:17:43
 */
@Slf4j
@Service
public class WeQrCodeServiceImpl extends ServiceImpl<WeQrCodeMapper, WeQrCode> implements IWeQrCodeService {

    @Autowired
    private IWeQrTagRelService tagRelService;

    @Autowired
    private IWeQrScopeService scopeService;

    @Autowired
    private IWeQrAttachmentsService attachmentsService;

    @Autowired
    private IWeCustomerService weCustomerService;

    @Autowired
    private IWeCorpAccountService weCorpAccountService;

    @Autowired
    private QwCustomerClient qwCustomerClient;

    @Autowired
    private RabbitMQSettingConfig mqSettingConfig;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * ??????????????????
     *
     * @param weQrAddQuery ??????
     */
    @Transactional(rollbackFor = {WeComException.class,Exception.class})
    @Override
    public void addQrCode(WeQrAddQuery weQrAddQuery) {
        //??????????????????????????????
        checkScope(weQrAddQuery.getQrUserInfos());
        WeAddWayQuery weContactWay = weQrAddQuery.getWeContactWay();
        WeAddWayVo weAddWayResult = qwCustomerClient.addContactWay(weContactWay).getData();
        if (weAddWayResult != null && ObjectUtil.equal(0, weAddWayResult.getErrCode())) {
            WeQrCode weQrCode = weQrAddQuery.getWeQrCodeEntity(weAddWayResult.getConfigId(), weAddWayResult.getQrCode());
            if (save(weQrCode)) {
                //??????????????????
                tagRelService.saveBatchByQrId(weQrCode.getId(), weQrAddQuery.getQrTags());
                //????????????????????????
                scopeService.saveBatchByQrId(weQrCode.getId(), weQrAddQuery.getQrUserInfos());
                //??????????????????
                attachmentsService.saveBatchByQrId(weQrCode.getId(), weQrAddQuery.getAttachments());
            }
            rabbitTemplate.convertAndSend(mqSettingConfig.getWeQrCodeChangeEx(),mqSettingConfig.getWeQrCodeChangeRk(),String.valueOf(weQrCode.getId()));
        }else {
            throw new WeComException(weAddWayResult.getErrCode(), WeErrorCodeEnum.parseEnum(weAddWayResult.getErrCode()).getErrorMsg());
        }
    }

    @Transactional(rollbackFor = {WeComException.class,Exception.class})
    @Override
    public void updateQrCode(WeQrAddQuery weQrAddQuery) {
        //??????????????????????????????
        checkScope(weQrAddQuery.getQrUserInfos());
        WeAddWayQuery weContactWay = weQrAddQuery.getWeContactWay();
        WeResultVo weResultVo = qwCustomerClient.updateContactWay(weContactWay).getData();
        if (weResultVo != null && ObjectUtil.equal(0, weResultVo.getErrCode())) {
            WeQrCode weQrCode = weQrAddQuery.getWeQrCodeEntity(null, null);
            if (updateById(weQrCode)) {
                //??????????????????
                tagRelService.updateBatchByQrId(weQrCode.getId(), weQrAddQuery.getQrTags());
                //????????????????????????
                scopeService.updateBatchByQrId(weQrCode.getId(), weQrAddQuery.getQrUserInfos());
                //??????????????????
                attachmentsService.updateBatchByQrId(weQrCode.getId(), weQrAddQuery.getAttachments());
            }
            rabbitTemplate.convertAndSend(mqSettingConfig.getWeQrCodeChangeEx(),mqSettingConfig.getWeQrCodeChangeRk(),String.valueOf(weQrCode.getId()));
        }else {
            throw new WeComException(weResultVo.getErrCode(), WeErrorCodeEnum.parseEnum(weResultVo.getErrCode()).getErrorMsg());
        }
    }

    @Override
    public WeQrCodeDetailVo getQrDetail(Long qrId) {
        WeQrCodeDetailVo weQrCodeDetailVo = this.baseMapper.getQrDetailByQrId(qrId);
        List<WeQrScopeVo> weQrScopeVoList = scopeService.getWeQrScopeByQrIds(ListUtil.toList(qrId));
        weQrCodeDetailVo.setQrUserInfos(weQrScopeVoList);
        return weQrCodeDetailVo;
    }

    @Override
    public List<WeQrCodeDetailVo> getQrDetailByQrIds(List<Long> qrIds) {
        List<WeQrCodeDetailVo> qrDetailByQrIds = this.baseMapper.getQrDetailByQrIds(qrIds);
        List<WeQrScopeVo> weQrScopeVoList = scopeService.getWeQrScopeByQrIds(qrIds);
        Map<Long, List<WeQrScopeVo>> weQrScopeMap = Optional.ofNullable(weQrScopeVoList).orElseGet(ArrayList::new).stream().collect(Collectors.groupingBy(WeQrScopeVo::getQrId));
        for (WeQrCodeDetailVo qrCodeDetail: qrDetailByQrIds) {
            if(weQrScopeMap.get(qrCodeDetail.getId()) != null){
                qrCodeDetail.setQrUserInfos(weQrScopeMap.get(qrCodeDetail.getId()));
            }
        }
        return qrDetailByQrIds ;
    }

    @Override
    public PageInfo<WeQrCodeDetailVo> getQrCodeList(WeQrCodeListQuery qrCodeListQuery) {
        List<WeQrCodeDetailVo> weQrCodeList = new ArrayList<>();
        List<Long> qrCodeIdList = this.baseMapper.getQrCodeList(qrCodeListQuery);
        if (CollectionUtil.isNotEmpty(qrCodeIdList)) {
            PageDomain pageDomain = TableSupport.buildPageRequest();
            String orderBy = SqlUtil.escapeOrderBySql(pageDomain.getOrderBy());
            PageHelper.orderBy(orderBy);
            List<WeQrCodeDetailVo> qrDetailByQrIds = this.baseMapper.getQrDetailByQrIds(qrCodeIdList);
            List<WeQrScopeVo> weQrScopeVoList = scopeService.getWeQrScopeByQrIds(qrCodeIdList);
            Map<Long, List<WeQrScopeVo>> weQrScopeMap = Optional.ofNullable(weQrScopeVoList).orElseGet(ArrayList::new).stream().collect(Collectors.groupingBy(WeQrScopeVo::getQrId));
            for (WeQrCodeDetailVo qrCodeDetail: qrDetailByQrIds) {
                if(weQrScopeMap.get(qrCodeDetail.getId()) != null){
                    qrCodeDetail.setQrUserInfos(weQrScopeMap.get(qrCodeDetail.getId()));
                }
            }
            weQrCodeList.addAll(qrDetailByQrIds);
        }
        PageInfo<Long> pageIdInfo = new PageInfo<>(qrCodeIdList);
        PageInfo<WeQrCodeDetailVo> pageInfo = new PageInfo<>(weQrCodeList);
        pageInfo.setTotal(pageIdInfo.getTotal());
        pageInfo.setPageNum(pageIdInfo.getPageNum());
        pageInfo.setPageSize(pageIdInfo.getPageSize());
        return pageInfo;
    }


    @Transactional(rollbackFor = {WeComException.class,Exception.class})
    @Override
    public void delQrCode(List<Long> qrIds) {
        List<WeQrCode> weQrCodes = this.listByIds(qrIds);
        if (CollectionUtil.isNotEmpty(weQrCodes)) {
            weQrCodes.forEach(item -> item.setDelFlag(1));
            if (this.updateBatchById(weQrCodes)) {
                //??????????????????
                tagRelService.delBatchByQrIds(qrIds);
                //????????????????????????
                scopeService.delBatchByQrIds(qrIds);
                //??????????????????
                attachmentsService.remove(new LambdaQueryWrapper<WeQrAttachments>()
                        .in(WeQrAttachments::getQrId,ListUtil.toList(qrIds)));;
            }
            //????????????????????????---????????????mq
            weQrCodes.forEach(item -> {
                if (StringUtils.isNotEmpty(item.getConfigId())) {
                    WeContactWayQuery weContactWayQuery = new WeContactWayQuery();
                    weContactWayQuery.setConfig_id(item.getConfigId());
                    qwCustomerClient.delContactWay(weContactWayQuery);
                }
            });
        }
    }

    @Override
    public WeQrCodeScanCountVo getWeQrCodeScanCount(WeQrCodeListQuery qrCodeListQuery) {
        WeQrCodeScanCountVo scanCountVo = new WeQrCodeScanCountVo();
        List<String> xAxis = new ArrayList<>();
        List<Integer> yAxis = new ArrayList<>();
        Long qrId = qrCodeListQuery.getQrId();
        Date beginTime = DateUtils.dateTime(DateUtils.YYYY_MM_DD, qrCodeListQuery.getBeginTime());
        Date endTime = DateUtils.dateTime(DateUtils.YYYY_MM_DD, qrCodeListQuery.getEndTime());
        WeQrCode weQrCode = getById(qrId);
        Map<String, List<WeCustomer>> customerMap = new HashMap<>();
        List<WeCustomer> customerList = weCustomerService.list(new LambdaQueryWrapper<WeCustomer>().eq(WeCustomer::getState, weQrCode.getState()));
        if (CollectionUtil.isNotEmpty(customerList)) {
            scanCountVo.setTotal(customerList.size());
            long count = customerList.stream().filter(item -> ObjectUtil.equal(DateUtils.getDate(), DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, item.getAddTime()))).count();
            scanCountVo.setToday(Long.valueOf(count).intValue());
            Map<String, List<WeCustomer>> listMap = customerList.stream().collect(Collectors.groupingBy(item -> DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, item.getAddTime())));
            customerMap.putAll(listMap);
        }
        DateUtils.findDates(beginTime, endTime).stream().map(d -> DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, d))
                .forEach(date -> {
                    xAxis.add(date);
                    if (CollectionUtil.isNotEmpty(customerMap.get(date))) {
                        yAxis.add(customerMap.get(date).size());
                    } else {
                        yAxis.add(0);
                    }
                });
        scanCountVo.setXAxis(xAxis);
        scanCountVo.setYAxis(yAxis);
        return scanCountVo;
    }

    /**
     * ????????????????????????
     *
     * @param qrUserInfos ????????????
     */
    private void checkScope(List<WeQrUserInfoQuery> qrUserInfos) {
        List<WeQrUserInfoQuery> qrUserInfoList = qrUserInfos.stream().filter(item -> ObjectUtil.equal(2, item.getType())).collect(Collectors.toList());
        if (CollectionUtil.isNotEmpty(qrUserInfoList)) {
            for (int i = 0; i < qrUserInfoList.size() - 1; i++) {
                for (int j = i + 1; j < qrUserInfoList.size(); j++) {
                    int finalJ = j;
                    long userSum = qrUserInfoList.get(i).getUserIds().stream()
                            .filter(one -> qrUserInfoList.get(finalJ).getUserIds().stream()
                                    .anyMatch(two -> ObjectUtil.equal(two, one))).count();
                    /*long partySum = qrUserInfoList.get(i).getPartys().stream()
                            .filter(one -> qrUserInfoList.get(finalJ).getPartys().stream()
                                    .anyMatch(two -> ObjectUtil.equal(two, one))).count();*/
                    if (userSum > 0 /*|| partySum > 0*/) {
                        long workCycleSum = qrUserInfoList.get(i).getWorkCycle().stream()
                                .filter(one -> qrUserInfoList.get(finalJ).getWorkCycle().stream()
                                        .anyMatch(two -> ObjectUtil.equal(two, one))).count();
                        if (workCycleSum > 0) {
                            String beginTime1 = qrUserInfoList.get(i).getBeginTime();
                            String endTime1 = qrUserInfoList.get(i).getEndTime();
                            String beginTime2 = qrUserInfoList.get(finalJ).getBeginTime();
                            String endTime2 = qrUserInfoList.get(finalJ).getEndTime();
                            if (match(beginTime1, endTime1, beginTime2, endTime2)) {
                                throw new WeComException("??????????????????????????????!");
                            }
                        }
                    }
                }
            }
        }
    }


    private boolean match(String startTime1, String endTime1, String startTime2, String endTime2) {
        DateTime parseStartTime1 = DateUtil.parse(startTime1, "HH:mm");
        DateTime parseEndTime1 = DateUtil.parse(endTime1, "HH:mm");

        DateTime parseStartTime2 = DateUtil.parse(startTime2, "HH:mm");
        DateTime parseEndTime2 = DateUtil.parse(endTime2, "HH:mm");
        return !(parseStartTime2.isAfter(parseEndTime1) || parseStartTime1.isAfter(parseEndTime2));
    }


    @Override
    public List<WeQrScopeVo> getWeQrScopeByTime(String formatTime, Long qrId) {
        return this.baseMapper.getWeQrScopeByTime(formatTime,qrId);
    }

    @Override
    public void deleteQrGroup(Long groupId) {
        WeQrCode weQrCode = new WeQrCode();
        weQrCode.setGroupId(1L);
        update(weQrCode,new LambdaQueryWrapper<WeQrCode>().eq(WeQrCode::getGroupId,groupId).eq(WeQrCode::getDelFlag,0));
    }

    @Override
    public void qrCodeUpdateTask(Long qrCodeId) {
        List<WeQrScopeVo> weQrScopeList = getWeQrScopeByTime(DateUtil.formatDateTime(new Date()),qrCodeId);
        log.info("?????????????????????????????? weQrScopeList {}", JSONObject.toJSONString(weQrScopeList));
        if (CollectionUtil.isNotEmpty(weQrScopeList)) {
            Map<Long, List<WeQrScopeVo>> qrCodeMap = weQrScopeList.stream().collect(Collectors.groupingBy(WeQrScopeVo::getQrId));
            qrCodeMap.forEach((qrId,scopeList) ->{
                WeQrCodeDetailVo weQrCodeDetail = this.baseMapper.getQrDetailByQrId(qrId);
                WeQrScopeVo weCustomizeQrScope = scopeList.stream()
                        .filter(item -> ObjectUtil.equal(1, item.getType())).findFirst().orElse(null);
                if(weCustomizeQrScope != null){
                    extracted(weCustomizeQrScope,weQrCodeDetail.getConfigId());
                }else {
                    WeQrScopeVo weDefaultQrScope = scopeList.stream()
                            .filter(item -> ObjectUtil.equal(0, item.getType())).findFirst().orElse(null);
                    extracted(weDefaultQrScope,weQrCodeDetail.getConfigId());
                }
            });
        }
    }

    private void extracted(WeQrScopeVo weQrScope, String configId) {
        log.info("extracted ->>>>>>>>>>weQrScope:{}",JSONObject.toJSONString(weQrScope));
        if(ObjectUtil.equal(1,weQrScope.getStatus())){
            return;
        }
        WeCorpAccount corpAccount = weCorpAccountService.getCorpAccountByCorpId(null);

        if(Objects.nonNull(corpAccount)){
            List<WeQrScopeUserVo> weQrUserList = weQrScope.getWeQrUserList();
            List<WeQrScopePartyVo> weQrPartyList = weQrScope.getWeQrPartyList();
            WeAddWayQuery weContactWay = new WeAddWayQuery();
            weContactWay.setConfig_id(configId);
            weContactWay.setCorpid(corpAccount.getCorpId());
            if (StringUtils.isNotEmpty(weQrUserList)) {
                Set<String> userIds = weQrUserList.stream().map(WeQrScopeUserVo::getUserId).collect(Collectors.toSet());
                weContactWay.setUser(new ArrayList<>(userIds));
            }
            if (StringUtils.isNotEmpty(weQrPartyList)) {
                Set<Long> partyIds = weQrPartyList.stream().map(WeQrScopePartyVo::getParty).map(Long::parseLong).collect(Collectors.toSet());
                weContactWay.setParty(new ArrayList<>(partyIds));
            }
            WeResultVo data = qwCustomerClient.updateContactWay(weContactWay).getData();
            if(data != null && data.getErrCode() == 0){
                scopeService.updateScopeStatusByQrId(weQrScope.getQrId(),weQrScope.getScopeId());
            }
        }

    }

}

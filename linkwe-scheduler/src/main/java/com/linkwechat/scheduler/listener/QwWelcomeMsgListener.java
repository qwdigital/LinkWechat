package com.linkwechat.scheduler.listener;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkwechat.common.constant.WeConstans;
import com.linkwechat.common.context.SecurityContextHolder;
import com.linkwechat.common.enums.MessageType;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.common.utils.spring.SpringUtils;
import com.linkwechat.domain.*;
import com.linkwechat.domain.community.vo.WeCommunityWeComeMsgVo;
import com.linkwechat.domain.customer.WeMakeCustomerTag;
import com.linkwechat.domain.media.WeMessageTemplate;
import com.linkwechat.domain.msgtlp.query.WeMsgTlpQuery;
import com.linkwechat.domain.msgtlp.vo.WeMsgTlpVo;
import com.linkwechat.domain.qr.WeQrAttachments;
import com.linkwechat.domain.qr.WeQrCode;
import com.linkwechat.domain.qr.vo.WeQrCodeDetailVo;
import com.linkwechat.domain.storecode.entity.WeStoreCode;
import com.linkwechat.domain.storecode.entity.WeStoreCodeConfig;
import com.linkwechat.domain.tag.vo.WeTagVo;
import com.linkwechat.domain.wecom.callback.WeBackCustomerVo;
import com.linkwechat.domain.wecom.query.customer.msg.WeWelcomeMsgQuery;
import com.linkwechat.domain.wecom.vo.WeResultVo;
import com.linkwechat.domain.wecom.vo.media.WeMediaVo;
import com.linkwechat.fegin.QwCustomerClient;
import com.linkwechat.service.*;
import com.linkwechat.service.impl.WeCorpAccountServiceImpl;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author danmo
 * @description ?????????????????????
 * @date 2022/4/3 15:39
 **/
@Slf4j
@Component
public class QwWelcomeMsgListener {

    @Autowired
    private IWeQrCodeService weQrCodeService;

    @Autowired
    private IWeCommunityNewGroupService weCommunityNewGroupService;

    @Autowired
    private IWeCustomerService weCustomerService;

    @Autowired
    private IWeMaterialService weMaterialService;

    @Autowired
    private IWeMsgTlpService weMsgTlpService;

    @Autowired
    private IWeTaskFissionService weTaskFissionService;

    @Autowired
    private IWeTaskFissionRecordService weTaskFissionRecordService;

    @Autowired
    private QwCustomerClient qwCustomerClient;

    @Autowired
    private IWeStoreCodeService weStoreCodeService;

    @Autowired
    private IWeQrAttachmentsService attachmentsService;

    @Autowired
    private IWeStoreCodeConfigService iWeStoreCodeConfigService;

    @Autowired
    private IWeTagService iWeTagService;


    @Value("${wecom.welcome-msg-default}")
    private String welcomeMsgDefault;


    @RabbitHandler
    @RabbitListener(queues = "${wecom.mq.queue.customer-welcome-msg:Qu_CustomerWelcomeMsg}")
    public void subscribe(String msg, Channel channel, Message message) {
        try {
            log.info("??????????????????????????????msg:{}", msg);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            WeBackCustomerVo query = JSONObject.parseObject(msg, WeBackCustomerVo.class);
            List<WeMessageTemplate> templates = new ArrayList<>();
            if (StringUtils.isNotEmpty(query.getState()) && query.getState().startsWith(WeConstans.WE_QR_CODE_PREFIX)) {

                WeQrCode weQrCode = weQrCodeService.getOne(new LambdaQueryWrapper<WeQrCode>()
                        .eq(WeQrCode::getState, query.getState())
                        .eq(WeQrCode::getDelFlag, 0).last("limit 1"));
                if (weQrCode != null) {
                    WeQrCodeDetailVo qrDetail = weQrCodeService.getQrDetail(weQrCode.getId());
                    List<WeQrAttachments> qrAttachments = qrDetail.getQrAttachments();
                    List<WeMessageTemplate> templateList = qrAttachments.stream().map(qrAttachment -> {
                        WeMessageTemplate template = new WeMessageTemplate();
                        template.setMsgType(qrAttachment.getMsgType());
                        template.setContent(qrAttachment.getContent());
                        template.setMediaId(qrAttachment.getMediaId());
                        template.setTitle(qrAttachment.getTitle());
                        template.setDescription(qrAttachment.getDescription());
                        template.setAppId(qrAttachment.getAppId());
                        template.setFileUrl(qrAttachment.getFileUrl());
                        template.setPicUrl(qrAttachment.getPicUrl());
                        template.setLinkUrl(qrAttachment.getLinkUrl());
                        return template;
                    }).collect(Collectors.toList());
                    templates.addAll(templateList);
                    makeCustomerTag(query.getExternalUserID(), query.getUserID(), qrDetail.getQrTags());
                } else {
                    log.warn("????????????????????????????????????");
                }
            } else if (StringUtils.isNotEmpty(query.getState()) && query.getState().startsWith(WeConstans.WE_QR_XKLQ_PREFIX)) {
                WeCommunityWeComeMsgVo welcomeMsgByState = weCommunityNewGroupService.getWelcomeMsgByState(query.getState());
                if (welcomeMsgByState != null) {
                    WeMessageTemplate textAtt = new WeMessageTemplate();
                    textAtt.setMsgType(MessageType.TEXT.getMessageType());
                    textAtt.setContent(welcomeMsgByState.getWelcomeMsg());
                    templates.add(textAtt);
                    WeMessageTemplate imageAtt = new WeMessageTemplate();
                    imageAtt.setMsgType(MessageType.IMAGE.getMessageType());
                    imageAtt.setPicUrl(welcomeMsgByState.getCodeUrl());
                    templates.add(imageAtt);
                    makeCustomerTag(query.getExternalUserID(), query.getUserID(), welcomeMsgByState.getTagList());
                }
            }
            else if (StringUtils.isNotEmpty(query.getState()) && query.getState().startsWith(WeConstans.FISSION_PREFIX)) {
                log.info("???????????????????????? state???{}",query.getState());
                String fissionRecordId = query.getState().substring(WeConstans.FISSION_PREFIX.length());
                WeMessageTemplate textAtt = new WeMessageTemplate();
                textAtt.setMsgType(MessageType.TEXT.getMessageType());
                WeTaskFissionRecord weTaskFissionRecord = weTaskFissionRecordService.getById(Long.valueOf(fissionRecordId));
                if(weTaskFissionRecord != null){
                    WeTaskFission weTaskFission = weTaskFissionService.selectWeTaskFissionById(weTaskFissionRecord.getTaskFissionId());
                    if(weTaskFission != null){
                        textAtt.setContent(weTaskFission.getWelcomeMsg());
                    }
                }
                if(StringUtils.isEmpty(textAtt.getContent())){//?????????????????????
                    //??????????????????????????????????????????????????????,?????????????????????????????????????????????????????????????????????
                    textAtt.setContent(welcomeMsgDefault);
                }
                templates.add(textAtt);
            }else if(StringUtils.isNotEmpty(query.getState()) && query.getState().startsWith(WeConstans.WE_STORE_CODE_CONFIG_PREFIX)){
                    log.info("????????????????????? state???{}",query.getState());
                    WeStoreCodeConfig storeCodeConfig = iWeStoreCodeConfigService.getOne(new LambdaQueryWrapper<WeStoreCodeConfig>()
                            .eq(WeStoreCodeConfig::getState, query.getState()));

                    if(null != storeCodeConfig){
                        List<WeQrAttachments> weQrAttachments = attachmentsService.list(new LambdaQueryWrapper<WeQrAttachments>()
                                .eq(WeQrAttachments::getQrId, storeCodeConfig.getId()));

                        if(CollectionUtil.isNotEmpty(weQrAttachments)){

                            List<WeMessageTemplate> templateList = weQrAttachments.stream().map(qrAttachment -> {
                                WeMessageTemplate template = new WeMessageTemplate();
                                template.setMsgType(qrAttachment.getMsgType());
                                template.setContent(qrAttachment.getContent());
                                template.setMediaId(qrAttachment.getMediaId());
                                template.setTitle(qrAttachment.getTitle());
                                template.setDescription(qrAttachment.getDescription());
                                template.setAppId(qrAttachment.getAppId());
                                template.setFileUrl(qrAttachment.getFileUrl());
                                template.setPicUrl(qrAttachment.getPicUrl());
                                template.setLinkUrl(qrAttachment.getLinkUrl());
                                return template;
                            }).collect(Collectors.toList());
                            templates.addAll(templateList);
                            String tagIds = storeCodeConfig.getTagIds();
                            if(StringUtils.isNotEmpty(tagIds)){
                                List<WeTag> weTags = iWeTagService.list(new LambdaQueryWrapper<WeTag>()
                                        .in(WeTag::getTagId, ListUtil.toList(tagIds.split(","))));
                                if(CollectionUtil.isNotEmpty(weTags)){
                                    makeCustomerTag(query.getExternalUserID(), query.getUserID(),
                                            weTags.stream().map(v -> {
                                                return new WeTagVo(v.getName(), v.getTagId());
                                            }).collect(Collectors.toList())
                                            );
                                }
                            }
                        }
                    }

            }else {
                WeMsgTlpQuery weMsgTlpQuery = new WeMsgTlpQuery();
                weMsgTlpQuery.setUserId(query.getUserID());
                weMsgTlpQuery.setFlag(false);
                List<WeMsgTlpVo> weMsgTlpList = weMsgTlpService.getList(weMsgTlpQuery);
                if (CollectionUtil.isNotEmpty(weMsgTlpList)) {
                    WeMsgTlpVo weMsgTlpVo = weMsgTlpList.get(0);
                    List<WeMessageTemplate> attachments = weMsgTlpVo.getAttachments();
                    templates.addAll(attachments);
                }
            }
            WeResultVo resultDto = sendWelcomeMsg(query, templates);
            log.info("????????????????????????result:{}", JSONObject.toJSONString(resultDto));
        } catch (Exception e) {
            e.printStackTrace();
            log.error("?????????????????????-?????????????????? msg:{},error:{}", msg, e);
        }
    }


    /**
     * ???????????????
     *
     * @param externaUserId ??????id
     * @param userId        ??????id
     * @param qrTags        ??????id
     */
    private void makeCustomerTag(String externaUserId, String userId, List<WeTagVo> qrTags) {
        if (CollectionUtil.isNotEmpty(qrTags)) {
            List<WeTag> weTagList = qrTags.stream().map(tag -> {
                WeTag weTag = new WeTag();
                weTag.setName(tag.getTagName());
                weTag.setTagId(tag.getTagId());
                return weTag;
            }).collect(Collectors.toList());
            WeMakeCustomerTag makeCustomerTag = new WeMakeCustomerTag();
            makeCustomerTag.setExternalUserid(externaUserId);
            makeCustomerTag.setUserId(userId);
            makeCustomerTag.setAddTag(weTagList);
            try {
                weCustomerService.makeLabel(makeCustomerTag);
            } catch (Exception e) {
                log.info("???????????????????????????????????? ex:{}", e);
            }
        }
    }

    /**
     * ???????????????
     *
     * @param query       ????????????
     * @param attachments ??????
     * @return
     */
    private WeResultVo sendWelcomeMsg(WeBackCustomerVo query, List<WeMessageTemplate> attachments) {
        WeWelcomeMsgQuery welcomeMsg = new WeWelcomeMsgQuery();
        welcomeMsg.setWelcome_code(query.getWelcomeCode());
        welcomeMsg.setCorpid(query.getToUserName());
        if (CollectionUtil.isNotEmpty(attachments)) {
            getMediaId(attachments);
        } else {
            WeMessageTemplate weMessageTemplate = new WeMessageTemplate();
            weMessageTemplate.setMsgType(MessageType.TEXT.getMessageType());
            weMessageTemplate.setContent(welcomeMsgDefault);
            attachments.add(weMessageTemplate);
        }
        WeCustomer weCustomer = weCustomerService.getOne(new LambdaQueryWrapper<WeCustomer>().eq(WeCustomer::getAddUserId, query.getUserID())
                .eq(WeCustomer::getExternalUserid, query.getExternalUserID()).eq(WeCustomer::getDelFlag, 0).last("limit 1"));

        if (weCustomer != null) {
            String customerName = weCustomer.getCustomerName();
            attachments.forEach(attachment -> {
                if (ObjectUtil.equal(MessageType.TEXT.getMessageType(), attachment.getMsgType())) {
                    attachment.setContent(attachment.getContent().replaceAll("#????????????#", customerName));
                }
            });
        }
        welcomeMsg.setMessageTemplates(attachments);
        return qwCustomerClient.sendWelcomeMsg(welcomeMsg).getData();
    }

    void getMediaId(List<WeMessageTemplate> messageTemplates) {
        Optional.ofNullable(messageTemplates).orElseGet(ArrayList::new).forEach(messageTemplate -> {
            if (ObjectUtil.equal(MessageType.IMAGE.getMessageType(), messageTemplate.getMsgType())) {
                WeMediaVo weMedia = weMaterialService.uploadTemporaryMaterial(messageTemplate.getPicUrl()
                        , MessageType.IMAGE.getMessageType()
                        , FileUtil.getName(messageTemplate.getPicUrl()));
                messageTemplate.setMediaId(weMedia.getMediaId());
            } else if (ObjectUtil.equal(MessageType.MINIPROGRAM.getMessageType(), messageTemplate.getMsgType())) {
                WeMediaVo weMedia = weMaterialService.uploadTemporaryMaterial(messageTemplate.getPicUrl()
                        , MessageType.IMAGE.getMessageType()
                        , FileUtil.getName(messageTemplate.getPicUrl()));
                messageTemplate.setMediaId(weMedia.getMediaId());
                messageTemplate.setPicMediaId(weMedia.getMediaId());
            } else if (ObjectUtil.equal(MessageType.VIDEO.getMessageType(), messageTemplate.getMsgType())) {
                WeMediaVo weMedia = weMaterialService.uploadTemporaryMaterial(messageTemplate.getMediaId()
                        , MessageType.VIDEO.getMessageType()
                        , FileUtil.getName(messageTemplate.getMediaId()));
                messageTemplate.setMediaId(weMedia.getMediaId());
            } else if (ObjectUtil.equal(MessageType.FILE.getMessageType(), messageTemplate.getMsgType())) {
                WeMediaVo weMedia = weMaterialService.uploadTemporaryMaterial(messageTemplate.getMediaId()
                        , MessageType.FILE.getMessageType()
                        , FileUtil.getName(messageTemplate.getMediaId()));
                messageTemplate.setMediaId(weMedia.getMediaId());
            }
        });
    }
}

package com.linkwechat.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linkwechat.common.enums.MessageNoticeType;
import com.linkwechat.common.utils.SecurityUtils;
import com.linkwechat.common.utils.SnowFlakeUtil;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.domain.WeGroup;
import com.linkwechat.domain.community.WeGroupSop;
import com.linkwechat.domain.community.WeGroupSopChat;
import com.linkwechat.domain.community.WeGroupSopMaterial;
import com.linkwechat.domain.community.WeGroupSopPic;
import com.linkwechat.domain.community.vo.WeCommunityTaskEmplVo;
import com.linkwechat.domain.community.vo.WeGroupSopVo;
import com.linkwechat.domain.material.vo.WeMaterialVo;
import com.linkwechat.mapper.*;
import com.linkwechat.service.IWeGroupSopService;
import com.linkwechat.service.IWeMessagePushService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WeGroupSopServiceImpl extends ServiceImpl<WeGroupSopMapper, WeGroupSop> implements IWeGroupSopService {

    @Autowired
    private WeGroupSopChatMapper sopChatMapper;

    @Autowired
    private WeGroupSopPicMapper sopPicMapper;

    @Autowired
    private WeGroupSopMaterialMapper sopMaterialMapper;

    @Autowired
    private WeGroupMapper groupMapper;

    @Autowired
    private WeMaterialMapper materialMapper;

    @Autowired
    private IWeMessagePushService weMessagePushService;


    @Override
    public boolean isNameOccupied(String ruleName) {
        List<WeGroupSop> weGroupSops = list(new LambdaQueryWrapper<WeGroupSop>()
                .eq(WeGroupSop::getRuleName, ruleName));

        if(CollectionUtil.isNotEmpty(weGroupSops)){
            return true;
        }

        return false;
    }

    @Override
    @Transactional
    public void addGroupSop(WeGroupSop weGroupSop, List<String> groupIdList, List<Long> materialIdList, List<String> picList) {

        if (this.save(weGroupSop)) {
            Long ruleId = weGroupSop.getRuleId();
            // ???????????????????????????
            this.saveChatAndMaterialBinds(ruleId, groupIdList, materialIdList);
            // ?????????????????????????????????
            List<WeGroupSopPic> sopPicList = picList.stream().map(picUrl -> new WeGroupSopPic(ruleId, picUrl)).collect(Collectors.toList());
            if (StringUtils.isNotEmpty(sopPicList)) {
                sopPicMapper.batchSopPic(sopPicList);
            }
        }
        // ???????????????????????????????????????
        this.sendMessage(groupIdList);

    }

    @Override
    @Transactional
    public void batchRemoveGroupSopByIds(Long[] ids) {

        if(removeByIds(Arrays.asList(ids))){

            sopChatMapper.delete(new LambdaQueryWrapper<WeGroupSopChat>()
                    .in(WeGroupSopChat::getRuleId, Arrays.asList(ids)));

            sopMaterialMapper.delete(new LambdaQueryWrapper<WeGroupSopMaterial>()
                    .in(WeGroupSopMaterial::getRuleId, Arrays.asList(ids)));

            sopPicMapper.delete(new LambdaQueryWrapper<WeGroupSopPic>()
                    .in(WeGroupSopPic::getRuleId, Arrays.asList(ids)));

        }


    }

    @Override
    @Transactional
    public void updateGroupSop(WeGroupSop weGroupSop, List<String> groupIdList, List<Long> materialIdList, List<String> picList) {
        if (this.updateById(weGroupSop)) {
            Long ruleId = weGroupSop.getRuleId();
            // ??????????????????
            this.deleteChatAndMaterialBinds(ruleId);
            // ??????????????????
            this.saveChatAndMaterialBinds(ruleId, groupIdList, materialIdList);
            // ???????????????
            LambdaQueryWrapper<WeGroupSopPic> queryWrapper = new LambdaQueryWrapper<>();
            sopPicMapper.delete(queryWrapper.eq(WeGroupSopPic::getRuleId, ruleId));

            // ????????????????????????
            List<WeGroupSopPic> sopPicList = picList.stream().map(picUrl -> new WeGroupSopPic(ruleId, picUrl)).collect(Collectors.toList());
            if (StringUtils.isNotEmpty(sopPicList)) {
                sopPicMapper.batchSopPic(sopPicList);
            }
        }

    }

    @Override
    public WeGroupSopVo getGroupSopById(Long ruleId) {
        WeGroupSopVo weGroupSopVo=new WeGroupSopVo();
        WeGroupSop weGroupSop = getById(ruleId);
        if(null != weGroupSop){
            BeanUtils.copyProperties(weGroupSop,weGroupSopVo);
            setChatAndMaterialAndPicList(weGroupSopVo);
        }
        return weGroupSopVo;
    }

    @Override
    public List<WeGroupSopVo> getGroupSopList(String ruleName, String createBy, String beginTime, String endTime) {
        List<WeGroupSopVo> groupSopVoList = this.baseMapper.getGroupSopList(ruleName, createBy, beginTime, endTime);
        groupSopVoList.forEach(this::setChatAndMaterialAndPicList);
        return groupSopVoList;
    }

    @Override
    public void sendMessage(List<String> groupIdList) {
        // ???????????????????????????????????????
        LambdaQueryWrapper<WeGroup> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(WeGroup::getChatId, groupIdList);
        List<WeGroup> groupList = groupMapper.selectList(queryWrapper);
        if(CollectionUtil.isNotEmpty(groupList)){

            weMessagePushService.pushMessageSelfH5(
                    groupList.stream().map(WeGroup::getOwner).collect(Collectors.toList()),
                    "??????????????????<br/> ??????????????????SOP????????????????????????????????????",
                    MessageNoticeType.SOP.getType(),true
            );

        }


    }

    @Override
    public List<WeGroupSopVo> getEmplTaskList(String emplId, boolean isDone) {
        List<WeGroupSopVo> sopVoList = this.baseMapper.getEmplTaskList(emplId, isDone);
        sopVoList.forEach(this::setChatAndMaterialAndPicList);
        return sopVoList;
    }

    @Override
    public int updateChatSopStatus(Long ruleId, String emplId) {
        return this.baseMapper.updateChatSopStatus(ruleId,emplId);
    }

    @Override
    public List<WeCommunityTaskEmplVo> getScopeListByRuleId(Long ruleId) {
        return this.baseMapper.getScopeListByRuleId(ruleId);
    }

    //???????????????????????????sop???????????????????????????????????????????????????WeGroupSopVo?????????????????????????????????
    private void setChatAndMaterialAndPicList(WeGroupSopVo groupSopVo) {

        Long ruleId = groupSopVo.getRuleId();

        // ????????????????????????
        List<WeGroup> groupList = this.getGroupListByRuleId(ruleId);
        if (StringUtils.isNotEmpty(groupList)) {
            groupSopVo.setGroupList(groupList);
        }

        // ?????????????????????
        List<WeCommunityTaskEmplVo> scopeList = sopChatMapper.getScopeListByRuleId(ruleId);
        if (StringUtils.isNotEmpty(scopeList)) {
            groupSopVo.setScopeList(scopeList);
        }

        // ????????????????????????
        List<Long> materialIdList = this.baseMapper.getMaterialIdListByRuleId(ruleId);
        if (StringUtils.isNotEmpty(materialIdList)) {
            List<WeMaterialVo> materialList = materialMapper.findMaterialVoListByIds(materialIdList.toArray(new Long[0]));
            groupSopVo.setMaterialList(materialList);
        }

        // ??????????????????
        List<WeGroupSopPic> sopPicList = sopPicMapper.selectList(
                new LambdaQueryWrapper<WeGroupSopPic>().eq(WeGroupSopPic::getRuleId, ruleId)
        );
        if (StringUtils.isNotEmpty(sopPicList)) {
            List<String> picUrlList = sopPicList.stream().map(WeGroupSopPic::getPicUrl).collect(Collectors.toList());
            groupSopVo.setPicList(picUrlList);
        }

    }

    /**
     * ????????????id???????????????????????????
     *
     * @param ruleId ??????id
     * @return ??????????????????
     */
    private List<WeGroup> getGroupListByRuleId(Long ruleId) {
        LambdaQueryWrapper<WeGroup> groupQueryWrapper = new LambdaQueryWrapper<>();
        List<String> chatIdList = this.baseMapper.getChatIdListByRuleId(ruleId);
        List<WeGroup> groupList = new ArrayList<>();
        if (StringUtils.isNotEmpty(chatIdList)) {
            groupQueryWrapper.in(WeGroup::getChatId, chatIdList);
            groupList = groupMapper.selectList(groupQueryWrapper);
        }
        return groupList;
    }

    /**
     * ?????????sop????????????????????????????????????
     *
     * @param ruleId ??????id
     */
    private void deleteChatAndMaterialBinds(Long ruleId) {
        // ??????????????????
        LambdaQueryWrapper<WeGroupSopChat> chatQueryWrapper = new LambdaQueryWrapper<>();
        chatQueryWrapper.eq(WeGroupSopChat::getRuleId, ruleId);
        sopChatMapper.delete(chatQueryWrapper);
        // ??????????????????
        LambdaQueryWrapper<WeGroupSopMaterial> materialQueryWrapper = new LambdaQueryWrapper<>();
        materialQueryWrapper.eq(WeGroupSopMaterial::getRuleId, ruleId);
        sopMaterialMapper.delete(materialQueryWrapper);
    }


    /**
     * ?????????sop??????????????????????????????????????????
     *
     * @param ruleId         ??????id
     * @param chatIdList     ??????id??????
     * @param materialIdList ??????id??????
     */
    private void saveChatAndMaterialBinds(Long ruleId, List<String> chatIdList, List<Long> materialIdList) {
        if (StringUtils.isNotEmpty(chatIdList)) {
            List<WeGroupSopChat> sopChatList = chatIdList
                    .stream()
                    .map(id ->{
                        WeGroupSopChat weGroupSopChat = new WeGroupSopChat();
                        weGroupSopChat.setId(SnowFlakeUtil.nextId());
                        weGroupSopChat.setRuleId(ruleId);
                        weGroupSopChat.setChatId(id);
                        weGroupSopChat.setDone(false);
                        weGroupSopChat.setCreateBy(String.valueOf(SecurityUtils.getUserId()));
                        weGroupSopChat.setCreateTime(new Date());
                        weGroupSopChat.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
                        weGroupSopChat.setUpdateTime(new Date());
                        return weGroupSopChat;
                    })
                    .collect(Collectors.toList());
            sopChatMapper.batchBindsSopChat(sopChatList);
        }
        if (StringUtils.isNotEmpty(materialIdList)) {

            List<WeGroupSopMaterial> weGroupSopMaterials = materialIdList
                    .stream()
                    .map(id -> WeGroupSopMaterial.builder().id(SnowFlakeUtil.nextId()).ruleId(ruleId).materialId(id).build())
                    .collect(Collectors.toList());
            sopMaterialMapper.batchBindsSopMaterial(weGroupSopMaterials);
        }
    }
}

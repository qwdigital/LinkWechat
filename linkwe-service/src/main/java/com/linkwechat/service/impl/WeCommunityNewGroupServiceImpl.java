package com.linkwechat.service.impl;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linkwechat.common.constant.WeConstans;
import com.linkwechat.common.enums.WeEmpleCodeType;
import com.linkwechat.common.exception.wecom.WeComException;
import com.linkwechat.common.utils.SnowFlakeUtil;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.domain.WeCustomer;
import com.linkwechat.domain.WeGroup;
import com.linkwechat.domain.WeTag;
import com.linkwechat.domain.community.WeEmpleCode;
import com.linkwechat.domain.community.WeEmpleCodeTag;
import com.linkwechat.domain.community.WeEmpleCodeUseScop;
import com.linkwechat.domain.community.query.WeCommunityNewGroupQuery;
import com.linkwechat.domain.community.vo.WeCommunityNewGroupVo;
import com.linkwechat.domain.community.vo.WeCommunityWeComeMsgVo;
import com.linkwechat.domain.community.vo.WeGroupCodeVo;
import com.linkwechat.domain.groupcode.entity.WeGroupCode;
import com.linkwechat.domain.wecom.vo.qr.WeAddWayVo;
import com.linkwechat.mapper.WeCommunityNewGroupMapper;
import com.linkwechat.mapper.WeGroupCodeMapper;
import com.linkwechat.mapper.WeTagMapper;
import com.linkwechat.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.linkwechat.domain.community.WeCommunityNewGroup;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WeCommunityNewGroupServiceImpl extends ServiceImpl<WeCommunityNewGroupMapper, WeCommunityNewGroup> implements IWeCommunityNewGroupService {

    @Autowired
    private WeGroupCodeMapper weGroupCodeMapper;

    @Autowired
    private WeTagMapper weTagMapper;


    @Autowired
    private IWeEmpleCodeTagService iWeEmpleCodeTagService;

    @Autowired
    private IWeEmpleCodeService iWeEmpleCodeService;

    @Autowired
    private IWeEmpleCodeUseScopService iWeEmpleCodeUseScopService;


    @Autowired
    private IWeGroupCodeService iWeGroupCodeService;

    @Autowired
    private IWeCustomerService iWeCustomerService;




    @Override
    @Transactional
    public void add(WeCommunityNewGroupQuery weCommunityNewGroupQuery) {

        //???????????????????????????
        WeGroupCode weGroupCode = weGroupCodeMapper.selectById(weCommunityNewGroupQuery.getGroupCodeId());
        if (!Optional.ofNullable(weGroupCode).isPresent()) {
            throw new WeComException("?????????????????????");
        }
        // ??????????????????????????????
        WeEmpleCode weEmpleCode = getWeEmpleCode(weCommunityNewGroupQuery);

        // ?????????????????????????????????????????????????????????config_id
        WeAddWayVo weContactWay = iWeEmpleCodeService.getWeContactWay(weEmpleCode);

        if(null != weContactWay){
            if(StringUtils.isNotEmpty(weContactWay.getConfigId())&&StringUtils.isNotEmpty(weContactWay.getQrCode())){
                weEmpleCode.setConfigId(weContactWay.getConfigId());
                weEmpleCode.setQrCode(weContactWay.getQrCode());

                // ????????????????????????
                if (iWeEmpleCodeService.save(weEmpleCode)) {

                    if (StringUtils.isNotEmpty(weEmpleCode.getWeEmpleCodeTags())) {
                        // ??????????????????????????????
                        iWeEmpleCodeTagService.saveOrUpdateBatch(weEmpleCode.getWeEmpleCodeTags());
                    }
                    // ??????????????????????????????
                    iWeEmpleCodeUseScopService.saveOrUpdateBatch(weEmpleCode.getWeEmpleCodeUseScops());

                    // ??????????????????????????????
                    WeCommunityNewGroup communityNewGroup = new WeCommunityNewGroup();
                    communityNewGroup.setGroupCodeId(weGroupCode.getId());
                    communityNewGroup.setEmplCodeName(weCommunityNewGroupQuery.getCodeName());
                    communityNewGroup.setEmplCodeId(weEmpleCode.getId());

                    save(communityNewGroup);
                }
            }else{
                throw new WeComException("??????????????????");
            }
        }





    }


    @Override
    public List<WeCommunityNewGroupVo> selectWeCommunityNewGroupList(WeCommunityNewGroup weCommunityNewGroup) {
        List<WeCommunityNewGroupVo> communityNewGroupVos = this.baseMapper.selectWeCommunityNewGroupList(weCommunityNewGroup);
        if (StringUtils.isNotEmpty(communityNewGroupVos)) {
            communityNewGroupVos.forEach(this::getCompleteEmplCodeInfo);
        }
        return communityNewGroupVos;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWeCommunityNewGroup(WeCommunityNewGroupQuery weCommunityNewGroupQuery) {

       //????????????????????????????????????
        WeCommunityNewGroup communityNewGroup = getById(weCommunityNewGroupQuery.getId());
        if (StringUtils.isNull(communityNewGroup)) {
            throw new WeComException("??????????????????????????????");
        }
        // ???????????????????????????
        WeGroupCode weGroupCode = weGroupCodeMapper.selectById(weCommunityNewGroupQuery.getGroupCodeId());
        if (null == weGroupCode) {
            throw new WeComException("?????????????????????");
        }

        communityNewGroup.setGroupCodeId(weCommunityNewGroupQuery.getGroupCodeId());
        try {
            // ???????????????????????????????????? "?????????" ??????
            WeEmpleCode weEmplCode = iWeEmpleCodeService.getById(communityNewGroup.getEmplCodeId());
            // ????????????????????????????????????????????????
            setScopsAndTags(weEmplCode, weCommunityNewGroupQuery);
            // ?????????????????????????????????????????????
            weEmplCode.setScenario(weCommunityNewGroupQuery.getCodeName());
            weEmplCode.setWelcomeMsg(weCommunityNewGroupQuery.getWelcomeMsg());
            weEmplCode.setIsJoinConfirmFriends(weCommunityNewGroupQuery.getSkipVerify()?new Integer(1):new Integer(0));
            iWeEmpleCodeService.updateWeEmpleCode(weEmplCode);
        } catch (Exception e) {
            throw new WeComException("????????????????????????");
        }
        communityNewGroup.setEmplCodeName(weCommunityNewGroupQuery.getCodeName());
         updateById(communityNewGroup);


    }

    @Override
    public WeCommunityWeComeMsgVo getWelcomeMsgByState(String state) {
        return this.baseMapper.getWelcomeMsgByState(state);
    }


    /**
     * ?????????????????????????????????????????????
     *
     * @param vo ??????????????????
     */
    private void getCompleteEmplCodeInfo(WeCommunityNewGroupVo vo) {
        // ????????????????????????
        WeEmpleCode empleCode = iWeEmpleCodeService.selectWeEmpleCodeById(vo.getEmplCodeId());

        Optional.ofNullable(empleCode).ifPresent(e -> {
            vo.setEmplCodeUrl(e.getQrCode());
            vo.setWelcomeMsg(e.getWelcomeMsg());
            vo.setSkipVerify(e.getIsJoinConfirmFriends().equals(new Integer(1))?true:false);
        });

       //??????????????????
        vo.setCusNumber(iWeCustomerService.count(new LambdaQueryWrapper<WeCustomer>()
                .eq(WeCustomer::getState, empleCode.getState())));

        // ?????????????????????
        WeGroupCode weGroupCode = iWeGroupCodeService.getById(vo.getGroupCodeId());
        Optional.ofNullable(weGroupCode).ifPresent(e -> {
            WeGroupCodeVo groupCodeVo = WeGroupCodeVo
                    .builder()
                    .id(e.getId())
                    .codeUrl(e.getCodeUrl())
                    .build();
            BeanUtils.copyProperties(e, groupCodeVo);
            vo.setGroupCodeInfo(groupCodeVo);
            vo.setActualGroupName(weGroupCode.getActivityName());
        });

        // ????????????????????????
        List<WeEmpleCodeUseScop> empleCodeUseScopList = iWeEmpleCodeUseScopService.list(new LambdaQueryWrapper<WeEmpleCodeUseScop>()
                .eq(WeEmpleCodeUseScop::getEmpleCodeId,vo.getEmplCodeId()));

        vo.setEmplList(empleCodeUseScopList);
//
//        List<WeGroupCodeActual> codeActuals = weGroupCodeActualService.list(new LambdaQueryWrapper<WeGroupCodeActual>()
//                .eq(WeGroupCodeActual::getGroupCodeId, vo.getGroupCodeId()));
//        if(CollectionUtil.isNotEmpty(codeActuals)){
//            vo.setActualGroupName(
//                    String.join(",", codeActuals.stream().map(WeGroupCodeActual::getGroupName).collect(Collectors.toList()))
//            );
//        }
//
//
//        //  ????????????????????????
//        //  ????????????????????????
//        List<WeGroup> groupList = iWeGroupCodeService.selectWeGroupListByGroupCodeId(vo.getGroupCodeId());
//        vo.setGroupList(groupList);

        // ??????????????????
        List<WeEmpleCodeTag> tagList = iWeEmpleCodeTagService.list(new LambdaQueryWrapper<WeEmpleCodeTag>()
                .eq(WeEmpleCodeTag::getEmpleCodeId,vo.getEmplCodeId()));
        vo.setTagList(tagList);
    }


    /**
     * ??????????????????
     *
     * @param communityNewGroupDto ??????
     * @return ????????????
     */
    private WeEmpleCode getWeEmpleCode(WeCommunityNewGroupQuery communityNewGroupDto) {
        Snowflake snowflake = IdUtil.getSnowflake(RandomUtil.randomLong(6), RandomUtil.randomInt(6));
        WeEmpleCode weEmpleCode = new WeEmpleCode();

        weEmpleCode.setId(SnowFlakeUtil.nextId());
        // ???????????????????????????
        setScopsAndTags(weEmpleCode, communityNewGroupDto);

        // ?????????????????????
        weEmpleCode.setCodeType(WeEmpleCodeType.MULTI.getType());

        weEmpleCode.setIsJoinConfirmFriends(communityNewGroupDto.getSkipVerify()?new Integer(1):new Integer(0));
        // ?????????
        weEmpleCode.setWelcomeMsg(communityNewGroupDto.getWelcomeMsg());
        // state????????????????????????????????????????????????????????????????????????30??????????????????id??????????????????
        weEmpleCode.setState(WeConstans.WE_QR_XKLQ_PREFIX + snowflake.nextIdStr());

        // ??????????????????????????????????????????
        weEmpleCode.setScenario(communityNewGroupDto.getCodeName());


        return weEmpleCode;
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param weEmpleCode          ????????????
     * @param communityNewGroupDto ??????????????????
     */
    private void setScopsAndTags(WeEmpleCode weEmpleCode, WeCommunityNewGroupQuery communityNewGroupDto) {
        // ???????????????????????? TODO user_id?????????business_id?
        List<Map<String, String>> userNameByUserIds = this.baseMapper.findUserNameByUserIds(communityNewGroupDto.getEmplList());
        List<WeEmpleCodeUseScop> weEmpleCodeUseScopList = userNameByUserIds.stream().map(e -> {
            WeEmpleCodeUseScop scop = new WeEmpleCodeUseScop();
            scop.setEmpleCodeId(weEmpleCode.getId());
            scop.setBusinessIdType(2);
            scop.setBusinessId(String.valueOf(e.get("we_user_id")));
            scop.setBusinessName(e.get("user_name"));
            return scop;
        }).collect(Collectors.toList());
        weEmpleCode.setWeEmpleCodeUseScops(weEmpleCodeUseScopList);

        // ??????????????????
        List<String> tagIdList = communityNewGroupDto.getTagList();
        if (StringUtils.isNotEmpty(tagIdList)) {
            LambdaQueryWrapper<WeTag> tagQueryWrapper = new LambdaQueryWrapper<>();
            tagQueryWrapper.in(WeTag::getTagId, tagIdList);
            List<WeTag> weTagList = weTagMapper.selectList(tagQueryWrapper);
            List<WeEmpleCodeTag> weEmpleCodeTagList = weTagList.stream().map(e -> {
                WeEmpleCodeTag tag = new WeEmpleCodeTag();
                tag.setEmpleCodeId(weEmpleCode.getId());
                tag.setTagId(e.getTagId());
                tag.setTagName(e.getName());
                return tag;
            }).collect(Collectors.toList());
            weEmpleCode.setWeEmpleCodeTags(weEmpleCodeTagList);
        }
    }

}

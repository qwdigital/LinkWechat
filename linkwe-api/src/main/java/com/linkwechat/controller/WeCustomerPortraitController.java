package com.linkwechat.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkwechat.common.constant.Constants;
import com.linkwechat.common.constant.WeConstans;
import com.linkwechat.common.core.controller.BaseController;
import com.linkwechat.common.core.domain.AjaxResult;
import com.linkwechat.common.core.page.TableDataInfo;
import com.linkwechat.common.utils.SnowFlakeUtil;
import com.linkwechat.domain.WeCustomerTrackRecord;
import com.linkwechat.domain.WeCustomerTrajectory;
import com.linkwechat.domain.WeTag;
import com.linkwechat.domain.WeTagGroup;
import com.linkwechat.domain.customer.WeMakeCustomerTag;
import com.linkwechat.domain.customer.vo.WeCustomerAddGroupVo;
import com.linkwechat.domain.customer.vo.WeCustomerPortraitVo;
import com.linkwechat.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;


/**
 * @description: 客户画像相关controller
 * @author: HaoN
 * @create: 2021-03-03 15:10
 **/
@RestController
@RequestMapping("/portrait")
public class WeCustomerPortraitController extends BaseController{


    @Autowired
    private IWeCustomerService weCustomerService;

    @Autowired
    private IWeTagService iWeTagService;

    @Autowired
    private IWeTagGroupService iWeTagGroupService;

    @Autowired
    private IWeGroupService iWeGroupService;

    @Autowired
    private IWeCustomerTrajectoryService iWeCustomerTrajectoryService;

    @Autowired
    private  IWeMomentsService iWeMomentsService;





    /**
     * 根据客户id和当前企业员工id获取客户详细信息
     * @param externalUserid
     * @param userId
     * @return
     */
    @GetMapping(value = "/findWeCustomerInfo")
    public AjaxResult<WeCustomerPortraitVo> findWeCustomerInfo(String externalUserid, String userId) throws Exception {

        return AjaxResult.success(
                weCustomerService.findCustomerByOperUseridAndCustomerId(externalUserid,userId)
        );
    }


    /**
     * 客户画像资料更新
     * @param weCustomerPortrait
     * @return
     */
    @PostMapping(value = "/updateWeCustomerInfo")
    public AjaxResult updateWeCustomerInfo(@RequestBody WeCustomerPortraitVo weCustomerPortrait){



        weCustomerService.updateWeCustomerPortrait(weCustomerPortrait);


        return AjaxResult.success();
    }


    /**
     * 获取当前系统所有可用标签
     * @param userId 员工id
     * @return
     */
    @GetMapping(value = "/findAllTags")
    public AjaxResult findAllTags(Integer groupTagType,String userId){

        if(groupTagType.equals(new Integer(1))){//企业标签
            return AjaxResult.success(
                    iWeTagGroupService.selectWeTagGroupList(
                            WeTagGroup.builder()
                                    .groupTagType(1)
                                    .build()
                    )
            );
        }
        return AjaxResult.success(
                iWeTagService.list(
                        new LambdaQueryWrapper<WeTag>()
                                .eq(WeTag::getDelFlag,new Integer(0))
                                .eq(WeTag::getOwner,userId)
                )

        );

    }



    /**
     * 客户画像个人标签库新增
     * @param weTagGroup
     * @return
     */
    @PostMapping("/addOrUpdatePersonTags")
    public AjaxResult addOrUpdatePersonTags(@RequestBody WeTagGroup weTagGroup){
        List<WeTag> weTags = weTagGroup.getWeTags();
        if(CollectionUtil.isNotEmpty(weTags)){
            weTags.stream().forEach(k->{
                k.setId(SnowFlakeUtil.nextId());
                k.setTagId(k.getId().toString());
                k.setGroupId(weTagGroup.getGroupId());
                k.setTagType(new Integer(3));
            });
            iWeTagService.saveOrUpdateBatch(weTags);
        }
        return AjaxResult.success();
    }

    /**
     * 个人标签删除
     * @param ids
     * @return
     */
    @DeleteMapping("/deletePersonTag/{ids}")
    public AjaxResult deletePersonTag(@PathVariable String[] ids){
        iWeTagService.removeByIds(CollectionUtil.newArrayList(ids));

        return AjaxResult.success();
    }


    /**
     * 更新客户画像标签
     * @param weMakeCustomerTag
     * @return
     */
    @PostMapping(value = "/updateWeCustomerPorTraitTag")
    public AjaxResult updateWeCustomerPorTraitTag(@RequestBody WeMakeCustomerTag weMakeCustomerTag){


        weCustomerService.makeLabel(weMakeCustomerTag);

        return AjaxResult.success();
    }



    /**
     * 查看客户添加的员工
     * @param externalUserid
     * @return
     */
    @GetMapping(value = "/findAddaddEmployes/{externalUserid}")
    public AjaxResult findaddEmployes(@PathVariable String externalUserid){
        return AjaxResult.success(
                weCustomerService.findWeUserByCustomerId(externalUserid)
        );
    }

    /**
     * 获取用户添加的群
     * @param externalUserid
     * @param userId
     * @return
     */
    @GetMapping(value = "/findAddGroupNum")
    public AjaxResult<WeCustomerAddGroupVo> findAddGroupNum(String externalUserid, String userId){

        return AjaxResult.success(
                iWeGroupService.findWeGroupByCustomer(userId,externalUserid)
        );
    }


    /**
     * 获取轨迹信息
     * @param trajectoryType
     * @return
     */
    @GetMapping(value = "/findTrajectory")
    public TableDataInfo findTrajectory(String userId, String externalUserid, Integer trajectoryType){
        startPage();
        LambdaQueryWrapper<WeCustomerTrajectory> ne = new LambdaQueryWrapper<WeCustomerTrajectory>()
                .eq(WeCustomerTrajectory::getWeUserId,userId)
                .eq(WeCustomerTrajectory::getExternalUseridOrChatid,externalUserid)
                .orderByDesc(WeCustomerTrajectory::getCreateTime);
        if(trajectoryType != null){
            ne.eq(WeCustomerTrajectory::getTrajectoryType, trajectoryType);
        }
        return getDataTable(
                iWeCustomerTrajectoryService.list(ne)
        );

    }


    /**
     *编辑跟进动态
     * @param trajectory
     * @return
     */
    @PostMapping(value = "/addOrEditWaitHandle")
    public AjaxResult addOrEditWaitHandle(@RequestBody WeCustomerTrackRecord trajectory){


        weCustomerService.addOrEditWaitHandle(trajectory);

        return AjaxResult.success();
    }


//    /**
//     * 删除轨迹
//     * @param trajectoryId
//     * @return
//     */
//    @DeleteMapping(value = "/removeTrajectory/{trajectoryId}")
//    public AjaxResult removeTrajectory(@PathVariable String trajectoryId){
//        iWeCustomerTrajectoryService.updateById(WeCustomerTrajectory.builder()
//                .id(trajectoryId)
//                .status(Constants.DELETE_CODE)
//                .build());
//        return AjaxResult.success();
//    }
//
//    /**
//     * 完成待办
//     * @param trajectoryId
//     * @return
//     */
//    @DeleteMapping(value = "/handleWait/{trajectoryId}")
//    public AjaxResult handleWait(@PathVariable String trajectoryId){
//        iWeCustomerTrajectoryService.updateById(WeCustomerTrajectory.builder()
//                .id(trajectoryId)
//                .status(Constants.HANDLE_SUCCESS)
//                .build());
//        return AjaxResult.success();
//    }
//


    /**
     * 个人朋友圈互动数据同步
     * @param userId
     * @return
     */
    @GetMapping("/synchMomentsInteracte/{userId}")
    public AjaxResult synchMomentsInteracte(@PathVariable String userId){

        iWeMomentsService.synchMomentsInteracte(CollectionUtil.newArrayList(userId));

        return AjaxResult.success(WeConstans.SYNCH_TIP);
    }


}

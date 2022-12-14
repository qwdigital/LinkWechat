package com.linkwechat.controller;

import com.linkwechat.common.core.controller.BaseController;
import com.linkwechat.common.core.domain.AjaxResult;
import com.linkwechat.domain.storecode.entity.WeStoreCodeConfig;
import com.linkwechat.domain.storecode.entity.WeStoreCodeCount;
import com.linkwechat.domain.storecode.vo.WeStoreCodesVo;
import com.linkwechat.service.IWeStoreCodeConfigService;
import com.linkwechat.service.IWeStoreCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 门店活码
 */
@RestController
@RequestMapping("/storeCode")
public class WxStoreCodeController extends BaseController {

    @Autowired
    private IWeStoreCodeConfigService iWeStoreCodeConfigService;


    @Autowired
    private IWeStoreCodeService iWeStoreCodeService;

    /**
     * 获取附件门店
     * @param storeCodeType 门店码类型(1:门店导购码;2:门店群活码)
     * @param unionid 微信unionid
     * @param longitude 经度
     * @param latitude 纬度
     * @param area 区域
     * @return
     */
    @GetMapping("/findStoreCode")
    public AjaxResult<WeStoreCodesVo> findStoreCode(Integer storeCodeType, String unionid, String longitude, String latitude, String area){

        return AjaxResult.success(
                iWeStoreCodeService.findStoreCode(storeCodeType,unionid,longitude,latitude,area)
        );
    }


    /**
     * 获取门店对应的配置相关
     * @param storeCodeType
     * @return
     */
    @GetMapping("/findWeStoreCodeConfig")
    public AjaxResult<WeStoreCodeConfig> findWeStoreCodeConfig(Integer storeCodeType){


        return AjaxResult.success(
                iWeStoreCodeConfigService.getWeStoreCodeConfig(storeCodeType)
        );
    }


    /**
     * 记录用户扫码行为
     * @return
     */
    @PostMapping("/countUserBehavior")
    public AjaxResult countUserBehavior(@RequestBody WeStoreCodeCount weStoreCodeCount){
        iWeStoreCodeService.countUserBehavior(weStoreCodeCount);
        return AjaxResult.success();
    }

}

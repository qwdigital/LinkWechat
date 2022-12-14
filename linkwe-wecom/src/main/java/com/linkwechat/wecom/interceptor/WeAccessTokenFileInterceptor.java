package com.linkwechat.wecom.interceptor;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.dtflys.forest.http.ForestRequest;
import com.dtflys.forest.http.ForestResponse;
import com.dtflys.forest.interceptor.Interceptor;
import com.google.common.collect.Lists;
import com.linkwechat.common.enums.WeErrorCodeEnum;
import com.linkwechat.common.utils.spring.SpringUtils;
import com.linkwechat.domain.wecom.query.WeBaseQuery;
import com.linkwechat.domain.wecom.vo.WeResultVo;
import com.linkwechat.wecom.service.IQwAccessTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 响应文件流
 */
@Slf4j
@Component
public class WeAccessTokenFileInterceptor extends WeForestInterceptor implements Interceptor {

    /**
     * 该方法在请求发送之前被调用, 若返回false则不会继续发送请求
     */
    @Override
    public boolean beforeExecute(ForestRequest request) {
        if (iQwAccessTokenService == null) {
            iQwAccessTokenService = SpringUtils.getBean(IQwAccessTokenService.class);
        }
        String token = iQwAccessTokenService.findCommonAccessToken(getCorpId(request));
        request.replaceOrAddQuery("access_token", token);
        return true;
    }

    /**
     * 请求成功调用(微信端错误异常统一处理)
     */
    @Override
    public void onSuccess(Object data, ForestRequest request, ForestResponse response) {
        log.info("url:{},result:{}", request.getUrl(), response.getContent());
    }

    /**
     * 请求重试
     *
     * @param request  请求
     * @param response 返回值
     */
    @Override
    public void onRetry(ForestRequest request, ForestResponse response) {
        log.info("url:{}, query:{},params:{}, 重试原因:{}, 当前重试次数:{}", request.getUrl(), request.getQueryString(),
                JSONObject.toJSONString(request.getArguments()), response.getContent(), request.getCurrentRetryCount());
        WeResultVo weResultVo = JSONUtil.toBean(response.getContent(), WeResultVo.class);
        //当错误码符合重置token时，刷新token
        if (!ObjectUtil.equal(WeErrorCodeEnum.ERROR_CODE_OWE_1.getErrorCode(), weResultVo.getErrCode())
                && Lists.newArrayList(errorCodeRetry).contains(weResultVo.getErrCode())) {
            //删除缓存
            String corpId = getCorpId(request);
            iQwAccessTokenService.removeCommonAccessToken(corpId);
            String token = iQwAccessTokenService.findCommonAccessToken(corpId);
            request.replaceOrAddQuery("access_token", token);
        }
    }

}
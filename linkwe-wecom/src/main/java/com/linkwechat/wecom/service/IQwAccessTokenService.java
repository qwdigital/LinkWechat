package com.linkwechat.wecom.service;


/**
 * @description: 微信token相关接口
 * @author: My
 * @create: 2020-08-26 14:43
 **/
public interface IQwAccessTokenService {

    public String findCommonAccessToken(String corpId);

    public String findContactAccessToken(String corpId);

    public String findAddressBookAccessToken(String corpId);

    public String findProviderAccessToken(String corpId);

    public String findChatAccessToken(String corpId);

    public String findKfAccessToken(String corpId);

    /**
     * 对外收款Token
     *
     * @param corpId
     * @return
     */
    public String findBillAccessToken(String corpId);

    public void removeCommonAccessToken(String corpId);

    public void removeContactAccessToken(String corpId);

    public void removeChatAccessToken(String corpId);

    public void removeKfAccessToken(String corpId);

    public void removeAddressBookAccessToken(String corpId);

    public String findAgentAccessToken(String corpId, Integer agentId);

    void removeAgentAccessToken(String corpId, Integer agentId);

    /**
     * 移除对外收款Token
     */
    public void removeBillAccessToken(String corpId);

}
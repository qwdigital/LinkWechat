package com.linkwechat.web.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linkwechat.common.constant.SynchRecordConstants;
import com.linkwechat.common.core.controller.BaseController;
import com.linkwechat.common.core.domain.AjaxResult;
import com.linkwechat.common.core.domain.dto.SysUserDTO;
import com.linkwechat.common.core.domain.entity.SysDept;
import com.linkwechat.common.core.domain.entity.SysMenu;
import com.linkwechat.common.core.domain.entity.SysRole;
import com.linkwechat.common.core.domain.entity.SysUser;
import com.linkwechat.common.core.domain.model.LoginUser;
import com.linkwechat.common.core.domain.model.WxLoginUser;
import com.linkwechat.common.core.page.TableDataInfo;
import com.linkwechat.common.core.page.TableSupport;
import com.linkwechat.common.utils.SecurityUtils;
import com.linkwechat.common.utils.ServletUtils;
import com.linkwechat.common.utils.StringUtils;
import com.linkwechat.common.utils.poi.ExcelUtil;
import com.linkwechat.domain.WeConfigParamInfo;
import com.linkwechat.domain.WeCorpAccount;
import com.linkwechat.domain.WxUser;
import com.linkwechat.domain.wecom.vo.user.WeUserDetailVo;
import com.linkwechat.framework.service.TokenService;
import com.linkwechat.service.IWeCorpAccountService;
import com.linkwechat.service.IWeSynchRecordService;
import com.linkwechat.service.IWxUserService;
import com.linkwechat.web.domain.vo.CorpVo;
import com.linkwechat.web.domain.vo.UserVo;
import com.linkwechat.web.mapper.SysUserMapper;
import com.linkwechat.web.service.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ????????????
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user")
@Api(tags = "????????????")
@Slf4j
public class SysUserController extends BaseController {
    @Resource
    private ISysUserService userService;

    @Resource
    private ISysRoleService roleService;

    @Resource
    private ISysPostService postService;

    @Resource
    private TokenService tokenService;

    @Resource
    private SysUserMapper sysUserMapper;


    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysDeptService iSysDeptService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private IWeCorpAccountService weCorpAccountService;

    @Autowired
    private IWeSynchRecordService iWeSynchRecordService;

    @Autowired
    private IWxUserService wxUserService;

    /**
     * ??????????????????
     */
    ////@PreAuthorize("@ss.hasPermi('system:user:list')")
    @GetMapping("/list")
    @ApiOperation(value = "????????????")
    public TableDataInfo list(SysUser user) {
        List<UserVo> userVos = userService.selectUserVoList(user, TableSupport.buildPageRequest());
        TableDataInfo dataTable
                = getDataTable(userVos);
        dataTable.setTotal(
                userService.selectCountUserDeptList(user)
        );
        dataTable.setLastSyncTime(
                iWeSynchRecordService.findUpdateLatestTime(SynchRecordConstants.SYNCH_MAIL_LIST)
        );//??????????????????

        return dataTable;
    }


    /**
     * ??????????????????
     *
     * @return
     */
    @GetMapping("/listAll")
    public AjaxResult<List<SysUser>> listAll(String userName) {
        return AjaxResult.success(userService.list(
                new LambdaQueryWrapper<SysUser>().like(StringUtils.isNotEmpty(userName), SysUser::getUserName, userName)
        ));
    }

    //    @Log(title = "????????????", businessType = BusinessType.EXPORT)
    ////@PreAuthorize("@ss.hasPermi('system:user:export')")
    @GetMapping("/export")
    public AjaxResult export(SysUser user) {
        List<SysUser> list = userService.selectUserList(user);
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        return util.exportExcel(list, "????????????");
    }


    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        List<SysUser> userList = util.importExcel(file.getInputStream());
        LoginUser loginUser = tokenService.getLoginUser(ServletUtils.getRequest());
        String operName = loginUser.getUserName();
        String message = userService.importUser(userList, updateSupport, operName);
        return AjaxResult.success(message);
    }

    @GetMapping("/importTemplate")
    public AjaxResult importTemplate() {
        ExcelUtil<SysUser> util = new ExcelUtil<SysUser>(SysUser.class);
        return util.importTemplateExcel("????????????");
    }

    /**
     * ????????????????????????????????????
     */
    @GetMapping(value = {"/", "/{userId}"})
    public AjaxResult getInfo(@PathVariable(value = "userId", required = false) Long userId) {
        AjaxResult ajax = AjaxResult.success();
        List<SysRole> roles = roleService.selectRoleAll();
        ajax.put("roles", SysUser.isAdmin(userId) ? roles : roles.stream().filter(r -> !r.isAdmin())
                .collect(Collectors.toList()));
        ajax.put("posts", postService.selectPostAll());
        if (StringUtils.isNotNull(userId)) {
            ajax.put(AjaxResult.DATA_TAG, userService.selectUserById(userId));
            ajax.put("postIds", postService.selectPostListByUserId(userId));
            ajax.put("roleIds", roleService.selectRoleIdsByUserId(userId));
        }
        return ajax;
    }

    /**
     * ????????????
     */
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysUserDTO user) {
        return toAjax(userService.addUser(user));
    }

    /**
     * ????????????
     */
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysUserDTO user) {
        userService.updateUser(user);
        return AjaxResult.success();
    }

    /**
     * ??????????????????
     *
     * @param user
     * @return
     */
    @PutMapping("/editUserRole")
    public AjaxResult editUserRole(@Validated @RequestBody SysUserDTO user) {

        userService.editUserRole(user);

        return AjaxResult.success();
    }

    /**
     * ????????????
     */
    ////@PreAuthorize("@ss.hasPermi('system:user:remove')")
//    @Log(title = "????????????", businessType = BusinessType.DELETE)
    @DeleteMapping("/{userIds}")
    public AjaxResult remove(@PathVariable String[] userIds) {
        userService.leaveUser(userIds);
        return AjaxResult.success();
    }

    /**
     * ??????????????????
     *
     * @param corpId
     * @param userIds
     * @return
     */
    @DeleteMapping("/callBackRemove/{corpId}/{userIds}")
    public AjaxResult callBackRemove(@PathVariable String corpId, @PathVariable String[] userIds) {

        userService.leaveUser(userIds);

        return AjaxResult.success();

    }

    /**
     * ????????????
     */
    ////@PreAuthorize("@ss.hasPermi('system:user:edit')")
//    @Log(title = "????????????", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd")
    public AjaxResult resetPwd(@RequestBody SysUser user) {
        userService.checkUserAllowed(user);
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        user.setUpdateBy(SecurityUtils.getUserName());
        return toAjax(userService.resetPwd(user));
    }

    /**
     * ????????????
     */
    ////@PreAuthorize("@ss.hasPermi('system:user:edit')")
//    @Log(title = "????????????", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysUser user) {
        userService.checkUserAllowed(user);
        user.setUpdateBy(SecurityUtils.getUserName());
        return toAjax(userService.updateUserStatus(user));
    }


    @GetMapping("/findCurrentLoginUser")
    public AjaxResult<LoginUser> findCurrentLoginUser(HttpServletRequest request) {
        return AjaxResult.success(tokenService.getLoginUser(request));
    }


    /**
     * ???????????????????????????
     *
     * @return
     */
    @PostMapping("/sync")
    public AjaxResult syncUserAndDept() {

        userService.syncUserAndDept();

        return AjaxResult.success();
    }

    /**
     * ??????????????????
     *
     * @return ????????????
     */
    @GetMapping("/getInfo")
    public AjaxResult getInfo() {
        SysUser user = SecurityUtils.getLoginUser().getSysUser();
        //???????????????????????????????????????
        // ????????????
        Set<String> roles = permissionService.getRolePermission(user);
        // ????????????
        Set<String> permissions = permissionService.getMenuPermission(user);

        SysDept sysDept = new SysDept();
        sysDept.setParentId(0L);
        List<SysDept> sysDepts = iSysDeptService.selectDeptList(sysDept);
        if (!CollectionUtils.isEmpty(sysDepts)) {
            user.setCompanyName(sysDepts.stream().findFirst().get().getDeptName());
        }
        sysDept = iSysDeptService.selectDeptById(user.getDeptId());

        if (null != sysDept) {
            user.setDeptName(sysDept.getDeptName());
        }

        WeCorpAccount weCorpAccount = weCorpAccountService.getCorpAccountByCorpId(null);
        CorpVo corpVo = weCorpAccount == null ? new CorpVo() : new CorpVo(weCorpAccount.getCorpId(), weCorpAccount.getCompanyName(),
                weCorpAccount.getAgentId());


        WeConfigParamInfo configParamInfo = new WeConfigParamInfo();

        if (null != weCorpAccount) {


            if (StringUtils.isNotEmpty(corpVo.getAppId())
                    && StringUtils.isNotEmpty(corpVo.getSecret())) {
                configParamInfo.setWeAppParamFill(true);
            }
            if (StringUtils.isNotEmpty(weCorpAccount.getChatSecret())
                    && StringUtils.isNotEmpty(weCorpAccount.getFinancePrivateKey())) {
                configParamInfo.setChatParamFill(true);
            }

            if (StringUtils.isNotEmpty(weCorpAccount.getMerChantName())
                    && StringUtils.isNotEmpty(weCorpAccount.getMerChantNumber())
                    && StringUtils.isNotEmpty(weCorpAccount.getMerChantSecret())
                    && StringUtils.isNotEmpty(weCorpAccount.getCertP12Url())) {
                configParamInfo.setRedEnvelopesParamFile(true);
            }

        }

        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        ajax.put("corpInfo", corpVo);
        ajax.put("configParamInfo", configParamInfo);
        return ajax;
    }

    @GetMapping("/getUserSensitiveInfo")
    public AjaxResult<WeUserDetailVo> getUserSensitiveInfo(@RequestParam("userTicket") String userTicket) {
        userService.getUserSensitiveInfo(userTicket);
        return AjaxResult.success();
    }

    /**
     * ??????????????????
     *
     * @return ????????????
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters() {
        // ????????????
        Long userId = SecurityUtils.getUserId();
        //???????????????????????????????????????
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }


    /**
     * ?????????????????????mq??????
     *
     * @param msg
     */
    @GetMapping("/syncUserAndDeptHandler")
    public AjaxResult syncUserAndDeptHandler(String msg) {
        userService.syncUserAndDeptHandler(msg);
        return AjaxResult.success();
    }

    @GetMapping("/info/{id}")
    public AjaxResult getUserInfoById(@PathVariable("id") Long userId) {
        SysUser user = sysUserMapper.selectUserById(userId);
        return AjaxResult.success(user);
    }

    @GetMapping("/getUserInfo/{weUserId}")
    public AjaxResult<SysUser> getInfo(@PathVariable("weUserId") String weUserId) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(SysUser::getWeUserId, weUserId);
        SysUser user = sysUserMapper.selectOne(queryWrapper);
        return AjaxResult.success(user);
    }

    @PostMapping("listByQuery")
    public AjaxResult<SysUser> listByQuery(@RequestBody SysUser sysUser) {
        List<SysUser> sysUsers = userService.selectUserList(sysUser);
        return AjaxResult.success(sysUsers);
    }

    /**
     * ????????????????????????
     *
     * @return
     */
    @GetMapping("/getWxInfo")
    public AjaxResult<WxUser> getWxInfo() {
        WxLoginUser wxLoginUser = SecurityUtils.getWxLoginUser();
        WxUser customerInfo = wxUserService.getCustomerInfo(wxLoginUser.getOpenId(), wxLoginUser.getUnionId());
        return AjaxResult.success(customerInfo);
    }

}

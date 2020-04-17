package com.mallplus.user.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.api.ApiController;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mallplus.common.annotation.SysLog;
import com.mallplus.common.model.SysPermission;
import com.mallplus.common.model.SysRole;
import com.mallplus.common.model.SysUser;
import com.mallplus.common.utils.CommonResult;
import com.mallplus.common.utils.JsonUtil;
import com.mallplus.common.utils.ValidatorUtils;
import com.mallplus.user.mapper.SysMenuMapper;
import com.mallplus.user.service.ISysRoleService;
import com.mallplus.user.service.ISysUserService;
import com.mallplus.user.service.ISysUserService1;
import com.mallplus.user.service.RedisService;
import com.mallplus.user.vo.Rediskey;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 后台用户表 前端控制器
 * </p>
 *
 * @author zscat
 * @since 2019-04-14
 */
@Slf4j
@Api(value = "用户管理", description = "", tags = {"用户管理"})
@RestController
@RequestMapping("/sys/sysUser")
public class SysUserController1 extends ApiController {

    @Resource
    private ISysUserService1 sysUserService;
    @Resource
    private ISysRoleService roleService;
    @Resource
    private SysMenuMapper permissionMapper;
    @Resource
    private RedisService redisService;

    @SysLog(MODULE = "sys", REMARK = "根据条件查询所有用户列表")
    @ApiOperation("根据条件查询所有用户列表")
    @GetMapping(value = "/list")
    public Object getUserByPage(SysUser entity,
                                @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                @RequestParam(value = "pageSize", defaultValue = "5") Integer pageSize
    ) {
        try {
            return new CommonResult().success(sysUserService.page(new Page<SysUser>(pageNum, pageSize), new QueryWrapper<>(entity)));
        } catch (Exception e) {
            log.error("根据条件查询所有用户列表：%s", e.getMessage(), e);
        }
        return new CommonResult().failed();
    }

    @SysLog(MODULE = "sys", REMARK = "保存用户")
    @ApiOperation("保存用户")
    @PostMapping(value = "/register")
    public Object saveUser(@RequestBody SysUser entity) {
        try {
            if (sysUserService.saves(entity)) {
                return new CommonResult().success();
            }
        } catch (Exception e) {
            log.error("保存用户：%s", e.getMessage(), e);
            return new CommonResult().failed();
        }
        return new CommonResult().failed();
    }

    @SysLog(MODULE = "sys", REMARK = "更新用户")
    @ApiOperation("更新用户")
    @PostMapping(value = "/update/{id}")
    public Object updateUser(@RequestBody SysUser entity) {
        try {
            if (sysUserService.updates(entity.getId(), entity)) {
                return new CommonResult().success();
            }
        } catch (Exception e) {
            log.error("更新用户：%s", e.getMessage(), e);
            return new CommonResult().failed();
        }
        return new CommonResult().failed();
    }

    @SysLog(MODULE = "sys", REMARK = "删除用户")
    @ApiOperation("删除用户")
    @GetMapping(value = "/delete/{id}")
    public Object deleteUser(@ApiParam("用户id") @PathVariable Long id) {
        try {
            if (ValidatorUtils.empty(id)) {
                return new CommonResult().paramFailed("用户id");
            }
            SysUser user= sysUserService.getById(id);
            if (user.getSupplyId()!=null && user.getSupplyId()==1){
                return new CommonResult().paramFailed("管理员账号不能删除");
            }
            if (sysUserService.removeById(id)) {
                return new CommonResult().success();
            }
        } catch (Exception e) {
            log.error("删除用户：%s", e.getMessage(), e);
            return new CommonResult().failed();
        }
        return new CommonResult().failed();
    }

    @SysLog(MODULE = "sys", REMARK = "给用户分配角色")
    @ApiOperation("查询用户明细")
    @GetMapping(value = "/{id}")
    public Object getUserById(@ApiParam("用户id") @PathVariable Long id) {
        try {
            if (ValidatorUtils.empty(id)) {
                return new CommonResult().paramFailed("用户id");
            }
            SysUser coupon = sysUserService.getById(id);
            coupon.setPassword(null);
            return new CommonResult().success(coupon);
        } catch (Exception e) {
            log.error("查询用户明细：%s", e.getMessage(), e);
            return new CommonResult().failed();
        }

    }

    @SysLog(MODULE = "sys", REMARK = "获取当前登录用户信息")
    @ApiOperation(value = "获取当前登录用户信息")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public Object getAdminInfo(Principal principal) {
        String username = principal.getName();
        SysUser queryU = new SysUser();
        queryU.setUsername(username);
        SysUser umsAdmin = sysUserService.getOne(new QueryWrapper<>(queryU));
        Map<String, Object> data = new HashMap<>();
        data.put("username", umsAdmin.getUsername());
        data.put("roles", new String[]{"TEST"});
        data.put("icon", umsAdmin.getIcon());
        return new CommonResult().success(data);
    }

    @SysLog(MODULE = "sys", REMARK = "登出功能")
    @ApiOperation(value = "登出功能")
    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @ResponseBody
    public Object logout() {
        return new CommonResult().success(null);
    }


    @SysLog(MODULE = "sys", REMARK = "给用户分配角色")
    @ApiOperation("给用户分配角色")
    @RequestMapping(value = "/role/update", method = RequestMethod.POST)
    @ResponseBody
    public Object updateRole(@RequestParam("adminId") Long adminId,
                             @RequestParam("roleIds") List<Long> roleIds) {
        int count = sysUserService.updateUserRole(adminId, roleIds);
        if (count >= 0) {
            //更新，删除时候，如果redis里有权限列表，重置
            if (!redisService.exists(String.format(Rediskey.menuList, adminId))) {
                List<SysPermission> list = permissionMapper.listUserPerms(adminId);
                String key = String.format(Rediskey.menuList, adminId);
                redisService.set(key, JsonUtil.objectToJson(list));
                return list;
            }
            return new CommonResult().success(count);
        }
        return new CommonResult().failed();
    }

    @SysLog(MODULE = "sys", REMARK = "获取指定用户的角色")
    @ApiOperation("获取指定用户的角色")
    @RequestMapping(value = "/role/{adminId}", method = RequestMethod.GET)
    @ResponseBody
    public Object getRoleList(@PathVariable Long adminId) {
        List<SysRole> roleList = sysUserService.getRoleListByUserId(adminId);
        return new CommonResult().success(roleList);
    }

    @SysLog(MODULE = "sys", REMARK = "获取指定用户的角色")
    @ApiOperation("获取指定用户的角色")
    @RequestMapping(value = "/userRoleCheck", method = RequestMethod.GET)
    @ResponseBody
    public Object userRoleCheck(@RequestParam("adminId") Long adminId) {
        List<SysRole> roleList = sysUserService.getRoleListByUserId(adminId);
        List<SysRole> allroleList = roleService.list(new QueryWrapper<>());
        for (SysRole a : allroleList) {
            for (SysRole u : roleList) {
                if (a.getId().equals(u.getId())) {
                    a.setChecked(true);
                }
            }
        }
        return new CommonResult().success(allroleList);
    }

    @SysLog(MODULE = "sys", REMARK = "给用户分配+-权限")
    @ApiOperation("给用户分配+-权限")
    @RequestMapping(value = "/permission/update", method = RequestMethod.POST)
    @ResponseBody
    public Object updatePermission(@RequestParam Long adminId,
                                   @RequestParam("permissionIds") List<Long> permissionIds) {
        int count = sysUserService.updatePermissionByUserId(adminId, permissionIds);
        if (count > 0) {
            return new CommonResult().success(count);
        }
        return new CommonResult().failed();
    }

    @SysLog(MODULE = "sys", REMARK = "获取用户所有权限（包括+-权限）")
    @ApiOperation("获取用户所有权限（包括+-权限）")
    @RequestMapping(value = "/permission/{adminId}", method = RequestMethod.GET)
    @ResponseBody
    public Object getPermissionList(@PathVariable Long adminId) {
        List<SysPermission> permissionList = sysUserService.getPermissionListByUserId(adminId);
        return new CommonResult().success(permissionList);
    }
}


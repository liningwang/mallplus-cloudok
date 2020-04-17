package com.mallplus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mallplus.common.model.SysPermission;
import com.mallplus.common.model.SysRole;
import com.mallplus.common.model.SysUser;

import java.util.List;

/**
 * <p>
 * 后台用户表 服务类
 * </p>
 *
 * @author zscat
 * @since 2019-04-14
 */
public interface ISysUserService1 extends IService<SysUser> {


    int updateUserRole(Long adminId, List<Long> roleIds);

    List<SysRole> getRoleListByUserId(Long adminId);

    int updatePermissionByUserId(Long adminId, List<Long> permissionIds);

    List<SysPermission> getPermissionListByUserId(Long adminId);

    List<SysPermission> listMenuByUserId(Long adminId);

    boolean saves(SysUser entity);

    boolean updates(Long id, SysUser admin);

    List<SysPermission> listUserPerms(Long id);

    void removePermissRedis(Long id);

    List<SysPermission> listPerms();
    Object reg(SysUser entity);

//    SmsCode generateCode(String phone);

    int updateShowStatus(List<Long> ids, Integer showStatus);
}

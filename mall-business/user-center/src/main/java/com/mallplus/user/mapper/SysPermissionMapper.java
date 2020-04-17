package com.mallplus.user.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallplus.common.model.SysPermission;

import java.util.List;

/**
 * <p>
 * 后台用户权限表 Mapper 接口
 * </p>
 *
 * @author zscat
 * @since 2019-04-14
 */
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    List<SysPermission> listMenuByUserId(Long id);

    List<SysPermission> getPermissionListByUserId(Long id);

    List<SysPermission> getPermissionList(Long roleId);

    List<SysPermission> listUserPerms(Long id);
}

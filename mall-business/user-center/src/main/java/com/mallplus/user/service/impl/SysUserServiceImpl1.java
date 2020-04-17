package com.mallplus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mallplus.common.model.SysPermission;
import com.mallplus.common.model.SysRole;
import com.mallplus.common.model.SysRoleUser;
import com.mallplus.common.model.SysUser;
import com.mallplus.common.utils.CommonResult;
import com.mallplus.common.utils.JsonUtil;
import com.mallplus.common.utils.ValidatorUtils;
import com.mallplus.common.vo.ApiContext;
import com.mallplus.user.mapper.*;
import com.mallplus.user.model.SysUserPermission;
import com.mallplus.user.model.SysUserRole;
import com.mallplus.user.service.*;
import com.mallplus.user.vo.Rediskey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 后台用户表 服务实现类
 * </p>
 *
 * @author zscat
 * @since 2019-04-14
 */
@Slf4j
@Service("sysUserService")
public class SysUserServiceImpl1 extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService1 {

    @Value("${redis.key.prefix.authCode}")
    private String REDIS_KEY_PREFIX_AUTH_CODE;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private SysUserMapper adminMapper;
    @Resource
    private SysUserRoleMapper adminRoleRelationMapper;
    @Resource
    private ISysUserRoleService adminRoleRelationService;
    @Resource
    private SysUserPermissionMapper adminPermissionRelationMapper;
    @Resource
    private SysRoleMapper roleMapper;
    @Resource
    private ISysUserPermissionService userPermissionService;
    @Resource
    private ISysRolePermissionService rolePermissionService;
    @Resource
    private ISysUserRoleService userRoleService;
    @Resource
    private SysPermissionMapper permissionMapper;
    @Resource
    private RedisService redisService;

    @Value("${aliyun.sms.expire-minute:1}")
    private Integer expireMinute;
    @Value("${aliyun.sms.day-count:30}")
    private Integer dayCount;
    @Autowired
    private ApiContext apiContext;


    @Override
    public int updateUserRole(Long adminId, List<Long> roleIds) {
        int count = roleIds == null ? 0 : roleIds.size();
        //先删除原来的关系
        SysRoleUser userRole = new SysRoleUser();
        userRole.setUserId(adminId);
        adminRoleRelationMapper.delete(new QueryWrapper<>(userRole));
        //建立新关系
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<SysRoleUser> list = new ArrayList<>();
            for (Long roleId : roleIds) {
                SysRoleUser roleRelation = new SysRoleUser();
                roleRelation.setUserId(adminId);
                roleRelation.setRoleId(roleId);
                list.add(roleRelation);
            }
            userRoleService.saveBatch(list);
        }
        return count;
    }

    @Override
    public List<SysRole> getRoleListByUserId(Long adminId) {
        return roleMapper.getRoleListByUserId(adminId);
    }

    @Override
    public int updatePermissionByUserId(Long adminId, List<Long> permissionIds) {
        //删除原所有权限关系
        SysUserPermission userPermission = new SysUserPermission();
        userPermission.setAdminId(adminId);
        adminPermissionRelationMapper.delete(new QueryWrapper<>(userPermission));
        //获取用户所有角色权限
        List<SysPermission> permissionList = permissionMapper.listMenuByUserId(adminId);
        List<Long> rolePermissionList = permissionList.stream().map(SysPermission::getId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(permissionIds)) {
            List<SysUserPermission> relationList = new ArrayList<>();
            //筛选出+权限
            List<Long> addPermissionIdList = permissionIds.stream().filter(permissionId -> !rolePermissionList.contains(permissionId)).collect(Collectors.toList());
            //筛选出-权限
            List<Long> subPermissionIdList = rolePermissionList.stream().filter(permissionId -> !permissionIds.contains(permissionId)).collect(Collectors.toList());
            //插入+-权限关系
            relationList.addAll(convert(adminId, 1, addPermissionIdList));
            relationList.addAll(convert(adminId, -1, subPermissionIdList));
            userPermissionService.saveBatch(relationList);
        }
        return 0;
    }

    @Override
    public List<SysPermission> listMenuByUserId(Long adminId) {
        if (!redisService.exists(String.format(Rediskey.menuTreesList, adminId))) {
            List<SysPermission> list = permissionMapper.listMenuByUserId(adminId);
            redisService.set(String.format(Rediskey.menuTreesList, adminId), JsonUtil.objectToJson(list));
            return list;
        } else {
            return JsonUtil.jsonToList(redisService.get(String.format(Rediskey.menuTreesList, adminId)), SysPermission.class);
        }

    }

    @Override
    public List<SysPermission> getPermissionListByUserId(Long adminId) {
        String listStr = redisService.get(String.format(Rediskey.permissionTreesList, adminId));
        if (null == listStr) {
            List<SysPermission> list = permissionMapper.getPermissionListByUserId(adminId);
            listStr = JsonUtil.objectToJson(list);
            redisService.set(String.format(Rediskey.permissionTreesList, adminId), JsonUtil.objectToJson(list));
            return list;
        } else {
            return JsonUtil.jsonToList(listStr, SysPermission.class);
        }
    }

    @Override
    public boolean saves(SysUser umsAdmin) {
        umsAdmin.setCreateTime(new Date());
        umsAdmin.setStatus(1);
        //查询是否有相同用户名的用户

        List<SysUser> umsAdminList = adminMapper.selectList(new QueryWrapper<SysUser>().eq("username", umsAdmin.getUsername()));
        if (umsAdminList.size() > 0) {
            return false;
        }
        //将密码进行加密操作
        if (StringUtils.isEmpty(umsAdmin.getPassword())) {
            umsAdmin.setPassword("123456");
        }
        String md5Password = passwordEncoder.encode(umsAdmin.getPassword());
        umsAdmin.setPassword(md5Password);
      //  umsAdmin.setStoreId(UserUtils.getCurrentMember().getStoreId());
        adminMapper.insert(umsAdmin);
        updateRole(umsAdmin.getId(), umsAdmin.getRoleIds());

        return true;
    }

    @Override
    @Transactional
    public boolean updates(Long id, SysUser admin) {
        admin.setUsername(null);
        admin.setId(id);
        String md5Password = passwordEncoder.encode(admin.getPassword());
        admin.setPassword(md5Password);
        updateRole(id, admin.getRoleIds());
        adminMapper.updateById(admin);
        return true;
    }

    @Override
    public List<SysPermission> listUserPerms(Long id) {
        if (!redisService.exists(String.format(Rediskey.menuList, id))) {
            List<SysPermission> list = permissionMapper.listUserPerms(id);
            String key = String.format(Rediskey.menuList, id);
            redisService.set(key, JsonUtil.objectToJson(list));
            return list;
        } else {
            return JsonUtil.jsonToList(redisService.get(String.format(Rediskey.menuList, id)), SysPermission.class);
        }
    }

    @Override
    public List<SysPermission> listPerms() {
        if (!redisService.exists(String.format(Rediskey.allMenuList, "admin"))) {
            List<SysPermission> list = permissionMapper.selectList(new QueryWrapper<>());
            String key = String.format(Rediskey.allMenuList, "admin");
            redisService.set(key, JsonUtil.objectToJson(list));
            return list;
        } else {
            return JsonUtil.jsonToList(redisService.get(String.format(Rediskey.allMenuList, "admin")), SysPermission.class);
        }
    }

    @Override
    public void removePermissRedis(Long id) {
        redisService.remove(String.format(Rediskey.menuTreesList, id));
        redisService.remove(String.format(Rediskey.menuList, id));
        redisService.remove(String.format(Rediskey.allTreesList, "admin"));
        redisService.remove(String.format(Rediskey.allMenuList, "admin"));
    }

    @Override
    public Object reg(SysUser umsAdmin) {
        if (ValidatorUtils.empty(umsAdmin.getUsername())) {
            return new CommonResult().failed("手机号为空");
        }
        if (ValidatorUtils.empty(umsAdmin.getPassword())) {
            return new CommonResult().failed("密码为空");
        }
        //验证验证码
        if (!verifyAuthCode(umsAdmin.getCode(), umsAdmin.getUsername())) {
            return new CommonResult().failed("验证码错误");
        }
        if (!umsAdmin.getPassword().equals(umsAdmin.getConfimpassword())) {
            return new CommonResult().failed("密码不一致");
        }
        umsAdmin.setCreateTime(new Date());
        umsAdmin.setStatus(1);
        //查询是否有相同用户名的用户

        List<SysUser> umsAdminList = adminMapper.selectList(new QueryWrapper<SysUser>().eq("username", umsAdmin.getUsername()));
        if (umsAdminList.size() > 0) {
            return new CommonResult().failed("手机号已注册");
        }
        //将密码进行加密操作
        if (StringUtils.isEmpty(umsAdmin.getPassword())) {
            umsAdmin.setPassword("123456");
        }
        String md5Password = passwordEncoder.encode(umsAdmin.getPassword());
        umsAdmin.setPassword(md5Password);
        adminMapper.insert(umsAdmin);
        SysRoleUser roleRelation = new SysRoleUser();
        roleRelation.setUserId(umsAdmin.getId());
        roleRelation.setRoleId(5L);
        adminRoleRelationMapper.insert(roleRelation);
        return new CommonResult().failed("注册成功");
    }

    //对输入的验证码进行校验
    public boolean verifyAuthCode(String authCode, String telephone) {
        if (StringUtils.isEmpty(authCode)) {
            return false;
        }
        String realAuthCode = redisService.get(REDIS_KEY_PREFIX_AUTH_CODE + telephone);
        return authCode.equals(realAuthCode);
    }
//    @Override
//    public SmsCode generateCode(String phone) {
//        //生成流水号
//        String uuid = UUID.randomUUID().toString();
//        StringBuilder sb = new StringBuilder();
//        Random random = new Random();
//        for (int i = 0; i < 6; i++) {
//            sb.append(random.nextInt(10));
//        }
//        Map<String, String> map = new HashMap<>(2);
//        map.put("code", sb.toString());
//        map.put("phone", phone);
//
//        //短信验证码缓存15分钟，
//        redisService.set(REDIS_KEY_PREFIX_AUTH_CODE + phone, sb.toString());
//        redisService.expire(REDIS_KEY_PREFIX_AUTH_CODE + phone, 60);
//        log.info("缓存验证码：{}", map);
//
//        //存储sys_sms
//        saveSmsAndSendCode(phone, sb.toString());
//        SmsCode smsCode = new SmsCode();
//        smsCode.setKey(uuid);
//        return smsCode;
//    }

    @Override
    public int updateShowStatus(List<Long> ids, Integer showStatus) {
        SysUser productCategory = new SysUser();
        productCategory.setStatus(showStatus);
        return adminMapper.update(productCategory, new QueryWrapper<SysUser>().in("id", ids));

    }

    /**
     * 保存短信记录，并发送短信
     *
     * @param phone
     */
//    private void saveSmsAndSendCode(String phone, String code) {
//        checkTodaySendCount(phone);
//
//        Sms sms = new Sms();
//        sms.setPhone(phone);
//        sms.setParams(code);
//        Map<String, String> params = new HashMap<>();
//        params.put("code", code);
//        params.put("admin", "admin");
//        smsService.save(sms, params);
//
//        //异步调用阿里短信接口发送短信
//        CompletableFuture.runAsync(() -> {
//            try {
//                smsService.sendSmsMsg(sms);
//            } catch (Exception e) {
//                params.put("err",  e.getMessage());
//                smsService.save(sms, params);
//                e.printStackTrace();
//                log.error("发送短信失败：{}", e.getMessage());
//            }
//
//        });
//
//        // 当天发送验证码次数+1
//        String countKey = countKey(phone);
//        redisService.increment(countKey, 1L);
//        redisService.expire(countKey, 1*3600*24);
//    }
    private String countKey(String phone) {
        return "sms:admin:count:" + LocalDate.now().toString() + ":" + phone;
    }

    private String smsRediskey(String str) {
        return "sms:admin:" + str;
    }

    /**
     * 获取当天发送验证码次数
     * 限制号码当天次数
     *
     * @param phone
     * @return
     */
    private void checkTodaySendCount(String phone) {
        String value = redisService.get(countKey(phone));
        if (value != null) {
            Integer count = Integer.valueOf(value);
            if (count > dayCount) {
                throw new IllegalArgumentException("已超过当天最大次数");
            }
        }

    }

    /**
     * 将+-权限关系转化为对象
     */
    private List<SysUserPermission> convert(Long adminId, Integer type, List<Long> permissionIdList) {
        List<SysUserPermission> relationList = permissionIdList.stream().map(permissionId -> {
            SysUserPermission relation = new SysUserPermission();
            relation.setAdminId(adminId);
            relation.setType(type);
            relation.setPermissionId(permissionId);
            return relation;
        }).collect(Collectors.toList());
        return relationList;
    }

    public void updateRole(Long adminId, String roleIds) {
        this.removePermissRedis(adminId);
        adminRoleRelationMapper.delete(new QueryWrapper<SysRoleUser>().eq("user_id", adminId));
        //建立新关系
        if (!StringUtils.isEmpty(roleIds)) {
            String[] rids = roleIds.split(",");
            List<SysRoleUser> list = new ArrayList<>();
            for (String roleId : rids) {
                SysRoleUser roleRelation = new SysRoleUser();
                roleRelation.setUserId(adminId);
                roleRelation.setRoleId(Long.valueOf(roleId));
                list.add(roleRelation);
            }
            adminRoleRelationService.saveBatch(list);
        }
    }
}

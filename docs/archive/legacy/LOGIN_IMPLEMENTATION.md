# 登录功能实现总结

## 一、后端修改

### 1. 新增文件

| 文件 | 说明 |
|------|------|
| `com/workflow/dto/LoginDTO.java` | 登录请求DTO |
| `com/workflow/vo/LoginUserVO.java` | 登录用户信息VO |
| `com/workflow/common/JwtUtil.java` | JWT工具类 |
| `com/workflow/common/UserContext.java` | 当前用户上下文（ThreadLocal） |
| `com/workflow/config/AuthInterceptor.java` | JWT认证拦截器 |
| `com/workflow/controller/AuthController.java` | 登录认证接口 |

### 2. 修改文件

| 文件 | 修改内容 |
|------|----------|
| `pom.xml` | 添加 jjwt 依赖 |
| `config/CorsConfig.java` | 注册认证拦截器 |
| `controller/ProcessTaskController.java` | 使用 UserContext.getUsername() 替换 CURRENT_USER 常量 |
| `service/SysUserService.java` | 添加 updatePassword() 方法 |

### 3. API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/current` | GET | 获取当前登录用户信息 |
| `/api/auth/logout` | POST | 退出登录 |

### 4. 登录逻辑
- 用户名密码验证
- admin 用户密码预设为 admin（BCrypt加密存储）
- 登录成功后返回 JWT Token
- Token 有效期 24 小时

---

## 二、前端修改

### 1. 新增文件

| 文件 | 说明 |
|------|------|
| `src/stores/user.js` | Pinia 用户状态管理 |
| `src/api/auth.js` | 登录相关 API |
| `src/views/Login.vue` | 登录页面 |

### 2. 修改文件

| 文件 | 修改内容 |
|------|----------|
| `src/utils/request.js` | 添加 Token 到请求头，处理 401 过期 |
| `src/router/index.js` | 添加登录路由和路由守卫 |
| `src/views/Layout.vue` | 显示真实用户信息，添加退出登录功能 |
| `src/views/EntityDataManage.vue` | 使用当前登录用户替换硬编码的 submitter |
| `src/main.js` | 应用启动时恢复用户信息 |

### 3. 路由守卫
- 未登录用户访问需要登录的页面 → 自动跳转到登录页
- 已登录用户访问登录页 → 自动跳转到首页

### 4. 用户信息存储
- Token 存储在 localStorage
- 用户信息存储在 localStorage（便于刷新后恢复）
- Pinia store 管理登录状态

---

## 三、数据库

### 1. 执行初始化 SQL
```bash
mysql -u root -p workflow < init_user.sql
```

### 2. 默认账号
- 用户名: `admin`
- 密码: `admin`

### 3. 用户表结构
- `sys_user` - 系统用户表
- `sys_role` - 系统角色表
- `sys_group` - 系统用户组表
- `sys_user_role` - 用户-角色关联表
- `sys_user_group` - 用户-组关联表

---

## 四、启动步骤

### 1. 后端
```bash
cd workflow-server
mvn clean install
mvn spring-boot:run
```

### 2. 前端
```bash
cd workflow-web
npm install  # 确保已安装 pinia
npm run dev
```

### 3. 访问
- 系统地址: http://localhost:3000
- 登录页面: http://localhost:3000/login
- 默认账号: admin / admin

---

## 五、后续优化建议

1. **密码修改功能**: 用户可自行修改密码
2. **记住我功能**: 延长 Token 有效期
3. **登录日志**: 记录用户登录历史
4. **密码强度校验**: 注册/修改密码时校验复杂度
5. **验证码**: 登录时添加图形验证码防止暴力破解
6. **多设备登录控制**: 限制同时在线设备数

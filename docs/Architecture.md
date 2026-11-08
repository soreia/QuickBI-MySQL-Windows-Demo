# Windows原生运行架构

## 1. 两种模式

```text
合并打包模式（推荐）：
浏览器 http://localhost:8081
  -> Spring Boot（static前端 + API + Mock BI + 内嵌任务执行器）
       -> 本机Windows MySQL 8.4 / quickbiwin

开发模式：
浏览器 http://localhost:5173
  -> Vite（静态资源、热更新）
       -> 同源路径代理到 Spring Boot:8081
            -> 本机Windows MySQL 8.4 / quickbiwin

MySQL专项测试：
JUnit/Flyway -> 本机Windows MySQL 8.4 / quickbiwinit
```

没有独立静态Web服务器要求。Build-Demo先构建Vue，复制到classpath static再构建JAR；Spring Boot直接提供根页面与assets。UI以标签切换，无前端history路由，因此没有需要额外配置的深链接fallback。

## 2. 保留的核心设计

- 企业主体tenant+oid、tenant+ISID唯一；非空Quick BI目标在租户内唯一，NULL允许未映射用户并存。
- MySQL保存HMAC-SHA256会话标识，不保存原始SID；角色来源为固定Mock种子数据而非请求体。
- 会话每次请求检查epoch、generation、idle、absolute；/me和任务轮询不延长业务idle。映射更新递增epoch并撤销用户全部会话。
- 授权快照完整成功后短事务切换当前指针，空集合成功可以清空；失败保留旧快照但不以其签新票。
- 新签票每次在线检查Mock用户/完整授权；RequestGuard唯一占位；单次5分钟Mock票据不落库，无Owner兜底。
- 最终事务复查会话和mappingVersion、写尝试结果及审计，可靠提交后交付URL。厂商调用与本地事务不构成分布式原子事务。
- 同租户QUEUED/RUNNING FULL采用生成列唯一约束；用户/任务租约与fence阻止过期结果发布。
- iframe onload只记EMBED_NAVIGATED，不能称为业务查看成功。

## 3. 账号、迁移与测试隔离

默认应用数据库quickbiwin，运行账号qbwinportal，迁移账号qbwinmigrator；仅localhost/127.0.0.1账号。运行账号拥有业务表必要DML，审计只有SELECT/INSERT。迁移账号的本库GRANT权限用于本地V3授权脚本，生产需由DBA/IaC拆分。

Initialize-Database -ForTests创建quickbiwinit及独立测试账号。Test-MySql设置TEST_DB变量，DatabaseIT严格要求本机连接与it结尾的库名，不会退回应用库。测试迁移通过占位符将表级权限授予测试用户；不需要容器依赖或自动起数据库服务。

本版迁移V1/V2/V3属于新的WindowsDemo数据库历史，不能将其直接应用到已经使用上一版迁移文件的业务库。Flyway checksum冲突时不盲目repair。

## 4. 明确简化

| 正式要求 | 本Demo处理 |
| --- | --- |
| 企业OIDC/PKCE及令牌校验 | 账号选择Mock，未实现真实认证 |
| Quick BI用户/授权/签票接口 | 同进程Mock+独立Mock权威表；不是真实SDK |
| 厂商跨域嵌入 | 本地同源合成HTML；不能证明真实浏览器跨域行为 |
| 生产SessionRepository扩展 | 专用JDBC会话逻辑，无Spring Session适配 |
| 独立Worker/每日02:00调度 | 内嵌每1.5秒认领，手工FULL/USER/补跑 |
| 审计outbox/WORM/归档 | 同库事务追加基础事件，未接归档 |
| 企业状态和角色事件 | 固定Mock主体；没有Entra实时事件 |
| 报表元数据 | 静态种子资源，权限可在Mock实验区改变 |
| 完整管理分页/筛选 | 报表/审计分页，任务/明细最多100条 |
| 配置 | 仅3项白名单，版本+理由 |
| 生产运维 | 无服务化安装、自动重启、压测、恢复演练或SLA |

票据只在Mock进程内存保留哈希索引及用户/资源/到期元数据；消费后删除，重启时所有未使用Mock票据失效。原始票据在响应与iframe请求中对浏览器可见，不能宣称“前端完全看不到”。

## 5. Windows脚本边界

脚本使用npm.cmd/mvn.cmd避免PowerShell误选同名.ps1，并基于PSScriptRoot定位工程目录；原生命令路径通过调用运算符执行。工具安装、系统服务控制、执行策略调整由用户按企业规范处理，脚本不自动提权或修改系统策略。

本地配置以JSON读取，不执行用户配置文本；库名/用户名/端口/演示密码做白名单校验。root密码由mysql客户端交互输入，不记录到配置。公开演示账号密码可出现在本地设置和初始化SQL参数，不得改用真实生产密码。

## 6. 生产边界

默认HTTP、无Secure Cookie、JDBC本机TLS关闭与公钥检索例外、公开演示凭据，不可公网暴露。生产恢复后应更换外部sessionGeneration、失效旧快照，不能只信任回滚出来的revoked状态。真实OIDC、Quick BI、RDS以及G01～G06均须独立实施验证。

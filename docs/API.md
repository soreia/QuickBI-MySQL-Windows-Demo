# 本地 Demo API

这是**当前源码已实现接口清单**，不是完整正式需求OpenAPI交付。基础JSON：成功 `{requestId,data}`，错误 `{requestId,error:{code,message,retryable,details}}`；logout204无响应体。

所有 `/api/` 需要PORTAL-SID Cookie；写请求还需已批准Origin与 `X-CSRF-Token`。默认允许localhost/127.0.0.1的5173、8081入口；启动脚本按local-settings.json中的端口设置允许列表。GET `/auth/context`初始化登录挑战；登录后 `/api/v1/me`返回派生CSRF令牌。

| 方法/路径 | 角色 | 输入/输出摘要 |
| --- | --- | --- |
| GET /healthz/ready | 公开 | MySQL基础可用性，仅最小状态 |
| GET /auth/context | 公开 | Mock用户清单+登录csrfToken；HttpOnly挑战Cookie |
| POST /auth/login | Mock公开 | `{userId}`；挑战头+Origin；选择种子主体，**不是真实SSO** |
| GET /api/v1/me | 任意已登录 | 主体、角色、映射、会话到期UTC、csrfToken；不刷新idle |
| POST /api/v1/auth/logout | 已登录/过期 | 204，清Cookie；有效会话校验CSRF |
| GET /api/v1/reports | 有映射主体 | `q,page,pageSize`；items,total,snapshotAt,validUntil |
| POST /api/v1/reports/{id}/embed-sessions | 有映射主体 | 空JSON `{}`；必需Idempotency-Key；embedUrl敏感、不可缓存 |
| GET /mock/bi/view | 持Mock票据 | query ticket；单次消费；返回合成HTML，非真实厂商接口 |
| POST /api/v1/embed-events | 已登录 | `{embedSessionId}`；只记录EMBED_NAVIGATED |
| GET /api/v1/admin/users | ADMIN | `q,page,pageSize`；当前映射和version |
| PUT /api/v1/admin/users/{id}/mapping | ADMIN | `{quickbiUserId,expectedVersion,reason}`；成功撤销目标会话 |
| POST /api/v1/admin/sync-jobs | ADMIN | `{type:"FULL"}`或`{type:"USER",userId}`；202 taskId+deduplicated |
| GET /api/v1/admin/sync-jobs | ADMIN | 最近最多100条，Demo未实现完整任务筛选分页 |
| GET /api/v1/admin/sync-jobs/{id} | ADMIN | 单任务状态与计数 |
| GET /api/v1/admin/sync-jobs/{id}/items | ADMIN | 最多100条逐用户结果 |
| POST /api/v1/admin/sync-jobs/{id}/retry | ADMIN | 仅终态失败/跳过/中断项；202新taskId |
| GET /api/v1/admin/audit-events | ADMIN/AUDITOR | from/to ISO UTC、eventType、status、q、page/pageSize；默认7天，最大31天 |
| GET /api/v1/admin/config | ADMIN | version,idleMinutes,absoluteHours,listCacheSeconds |
| PATCH /api/v1/admin/config | ADMIN | 上述值+expectedVersion+reason；范围见README |
| GET /api/v1/demo/state | ADMIN、本地Mock | Mock用户、授权、资源与故障模式 |
| POST /api/v1/demo/fault | ADMIN、本地Mock | `{mode:"NORMAL"/"READ_UNAVAILABLE"/"TICKET_UNKNOWN"/"AUDIT_FAILURE"}` |
| POST /api/v1/demo/grant | ADMIN、本地Mock | `{biUserId,reportId,granted}`；**模拟外部厂商管理，不属于生产门户授权功能** |

分页page默认1、pageSize默认20、上限100。任务/明细的100条限制为Demo简化。主体和tenant来自可信会话/固定Demo租户，不接受客户端指定tenant。

## 推荐HTTP检查

请运行`scripts/smoke.py`自动处理Cookie、挑战和CSRF。不要把有效票据或Cookie写入curl命令历史或提交到工单。错误诊断使用requestId，不粘贴完整SDK响应。

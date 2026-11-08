# 建议的本地演示步骤

## 场景A：普通访问闭环

1. 打开localhost:8081（打包模式）或localhost:5173（开发模式），选择Alice并进入。
2. 应看到区域销售看板、经营概览，不应看到Bob的财务工作簿或草稿。
3. 打开经营概览；页面标注Mock厂商用户bi-alice和华东合成数据。
4. 关闭后重新打开将生成新幂等键与新票据，不复用上次敏感响应。
5. 退出；多标签页通过BroadcastChannel移除iframe，后台检查也会使旧会话401。
6. 用Bob登录，查看经营概览的华南合成数据。不是来自真实Quick BI的行级权限证明。

## 场景B：旧列表不放行已撤权资源

1. 普通窗口Alice登录并获取列表，暂不刷新。
2. 隐私窗口管理员登录，进入Mock实验区，选bi-alice与rep-sales，取消授权勾选后提交。
3. 回Alice旧列表，点击区域销售看板；应收到REPORT_NOT_ACCESSIBLE，而不是获得票据。
4. 管理员重新勾选授权恢复，测试继续。

## 场景C：身份关联、冲突与撤销

1. 管理员进入用户与映射，将未映射用户关联到bi-free并填写原因；应成功，版本递增。
2. 未映射用户重新登录；已映射但没有报表授权，显示空列表，不显示系统错误。
3. 尝试将Bob关联到bi-alice，预期409唯一冲突；原数据不被覆盖。
4. Alice保持登录时，管理员对其映射保存原来的bi-alice并填写原因；Alice下次请求401，须重新登录。
5. 代码接口携带旧expectedVersion时409。可用HTTP smoke自动验证。

## 场景D：同步任务与失败重试

1. 管理员进入同步任务，触发全量同步；观察QUEUED/RUNNING及终态计数。
2. 活动FULL期间重复触发只返回已有任务。Mock很快，页面连续点击未必撞上活动窗口；数据库集成测试专门校验该约束。
3. 在Mock实验区启用只读不可用，触发FULL，预期失败项可诊断、旧快照保留但新签票不放行。
4. 恢复NORMAL后补跑失败项，生成新taskId关联parent任务。

## 场景E：签票结果不明和审计失效

1. Alice在另一窗口保持登录并保留列表。
2. 管理员选择TICKET_UNKNOWN；Alice打开报表应503 TICKET_ISSUE_UNKNOWN，不应自动重试或展示iframe。
3. 恢复NORMAL，再选择AUDIT_FAILURE；Alice打开报表应503 AUDIT_UNAVAILABLE，不交付新票据。
4. 管理员点击恢复正常。该恢复端点是Mock专用例外，不是生产审计故障时的通用绕过通道。

## 场景F：只读审计角色

1. 以审计员登录，可查询LOGIN、签票和同步等历史基础记录。
2. 不能读取用户管理或触发同步，即使手工调用接口也应403。
3. iframe onload对应EMBED_NAVIGATED/CLIENT_SIGNAL，不能写成“报表查看成功”。

## 需要保留的验证证据

本地执行Test-Security.ps1、Maven单测、Test-MySql.ps1及Test-Http.ps1后，保留命令退出码、版本、测试报告与脱敏错误码。不要截图或粘贴有效Ticket、Cookie或含票据URL；浏览器Network面板能看到iframe的敏感请求是技术事实，不应宣称前端完全不可见。

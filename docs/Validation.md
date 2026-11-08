# Windows版交付验证记录

日期：2026-11-08。版本：Windows V1.1。

## 本版已执行的检查

| 检查 | 结果 | 准确边界 |
| --- | --- | --- |
| Java安全工具自测 | PASS | 在当前非Windows环境真实用JDK21编译Crypto与SecuritySelfTest，检查HMAC、比较、HTML编码和10000随机令牌 |
| 后端主源码辅助检查 | PASS | 真实javac配合临时Spring/Servlet/Jackson最小签名桩；不是真实依赖构建，桩不随包交付 |
| 前端请求与App脚本辅助检查 | PASS | 真实TypeScript检查api.ts及抽取的App脚本，VueAPI使用临时签名；不是vue-tsc模板构建 |
| YAML/JSON/XML/Python | PASS | 原生3306/quickbiwin默认值、Flyway占位符、settings与手工SQL演示密码一致、POM/JSON语法、Python AST |
| 原生依赖清理 | PASS | 工程不含容器启动文件、Web代理配置；POM与MySQL集成测试均不含Testcontainers依赖 |
| PowerShell源码静态审阅 | 已审阅，未执行 | 检查调用路径、npm.cmd/mvn.cmd、原生命令失败退出、参数白名单、账号/测试库隔离、无自动提权或执行策略修改；ASCII编码便于PS5.1读取。没有PowerShell解析器运行证据 |
| 源码打包 | PASS | 文件命名、Markdown围栏、ZIP完整性和校验清单检查 |

## 仍需在Windows开发机补跑

1. `scripts/Check-Environment.ps1`：工具、版本及本机MySQL端口。
2. `scripts/Initialize-Database.ps1`：实际管理员交互、建专用库/账号。
3. `scripts/Build-Demo.ps1`：真实npm依赖、Vue模板类型、Spring依赖、Maven单元测试、合并JAR。
4. `scripts/Start-Demo.ps1`：实际JAR启动、Flyway迁移、健康UP、8081静态页面。
5. 开发模式两个终端、5173代理、端口变更配置、Ctrl+C停止。
6. `scripts/Initialize-Database.ps1 -ForTests`、`scripts/Test-MySql.ps1`：真实原生MySQL独立测试库。
7. `scripts/Test-Http.ps1`：角色/授权/签票/CSRF/版本/撤权等HTTP断言。
8. Windows浏览器多会话、iframe、Mock故障、数据恢复与长期运行。

以上当前状态均为NOT RUN。交付环境没有Windows/PowerShell/Maven/MySQL服务，依赖仓库DNS解析失败，因此不提供“Windows已启动成功”或“所有测试通过”的声明，也不附编译出的JAR/dist。

## 实际验证后需保存

保留工具版本、Maven surefire/failsafe结果、npm锁文件、HTTP smoke输出、Windows版本与脱敏故障记录。密码、Cookie、原始Ticket及带票据URL不得放入测试证据。只有实际命令输出PASS且退出码成功，才更新本记录为该开发机通过。

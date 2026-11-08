# Quick BI · MySQL Windows 原生 Demo

**版本：Windows V1.1 / 2026-11-08。无需容器引擎、无需 Nginx。**

这是上一版本Demo的Windows原生改造包：本机MySQL + Java后端 + Vue前端。Entra ID和Quick BI默认使用本地Mock，不需要任何云账户。仅使用合成数据，**不允许部署到公网，不代表已完成企业SSO或真实Quick BI集成**。

> **验证边界：**当前交付环境不是Windows，没有PowerShell、Maven和MySQL服务，外部依赖仓库DNS解析失败。已执行可用的Java工具自测与静态检查；尚未在Windows运行这些PowerShell脚本，也未完成Maven/npm全量构建和MySQL联调。源码按以下方式配置，实际通过以本机执行结果为准。详见`docs/Validation.md`。

## 1. 本版改变了什么

| 项目 | Windows原生方案 |
| --- | --- |
| 数据库 | Windows安装的MySQL 8.4，默认127.0.0.1:3306 |
| 数据隔离 | 专用库quickbiwin，独立本地账号；不使用上一版数据库 |
| 运行入口 | PowerShell脚本；不包含容器启动文件和Web代理配置 |
| 打包运行（推荐） | Vue编译产物复制到Spring Boot static目录，打成一个JAR，浏览器访问8081 |
| 开发调试 | 后端8081、Vite前端5173；两个终端分别运行，代理保持同源 |
| MySQL集成测试 | 原生MySQL单独测试库quickbiwinit，不依赖Testcontainers |
| 停止 | 运行窗口按Ctrl+C；不停止或删除本机MySQL服务 |

业务能力保留：Mock登录、哈希会话、用户映射及唯一约束、报表快照、每次打开在线鉴权、单次短时票据、同步任务、只读审计、非敏感配置、Mock撤权和故障实验区。

## 2. 一次性安装环境

建议Windows 10/11 64位；Windows PowerShell 5.1或PowerShell 7。本项目不自动下载或安装系统软件，也不修改电脑全局执行策略。

| 软件 | 要求 | 检查命令 |
| --- | --- | --- |
| JDK | 推荐JDK21，必须包含javac，不只是JRE | `java --version`、`javac -version` |
| Maven | 3.9.x，配置JAVA_HOME及bin到PATH | `mvn.cmd -version` |
| Node.js | 推荐22.x且不低于22.12 | `node.exe --version` |
| npm | 随Node安装 | `npm.cmd --version` |
| MySQL Server | 8.4，完成初始化并知道本机root密码 | `mysql.exe --version` |
| Python（可选） | 3.10+，只用于HTTP冒烟测试 | `py -3 --version` |

**MySQL安装注意：**使用MySQL Server 8.4的Windows MSI及配套Configurator完成配置。MySQL Workbench是管理工具，不等于已安装数据库服务。建议配置为Windows服务，监听本机3306；保留安装时设置的root密码。服务名可能是MySQL84，也可能由安装时自定义，不要无条件启动一个并不存在的服务名。

查看服务：

```powershell
Get-Service *MySQL*
```

若未运行，在“服务”界面启动实际MySQL服务；或者在具有权限的终端中执行以下示例（按实际服务名替换）：

```powershell
Start-Service -Name MySQL84
```

Maven、JDK、Node安装后重新打开PowerShell，确保PATH刷新。Maven应使用与javac相同的JDK21；JAVA_HOME指向JDK根目录，不是bin目录。

首次构建需要访问Maven/npm仓库；企业网络可配置公司批准的镜像或代理。配置不正确时应修正网络，不应关闭下载证书验证。

## 3. 推荐方式：编译为一个JAR运行

### 第一步：解压并进入项目目录

例如解压至`C:\Projects\QuickBI-MySQL-Windows-Demo`，在PowerShell执行：

```powershell
cd C:\Projects\QuickBI-MySQL-Windows-Demo
Copy-Item .\local-settings.example.json .\local-settings.json
```

如果已经有local-settings.json，不要覆盖已有配置；脚本在缺少此文件时也会自动复制模板。不要直接在ZIP压缩窗口中运行。

### 第二步：检查环境与端口

```powershell
.\scripts\Check-Environment.ps1
```

脚本检查工具、JDK/Node版本和MySQL TCP可达性，不测试root密码，也不会安装或自动启动数据库。

MySQL客户端没有加PATH时，在local-settings.json里设置完整路径，例如：

```json
"MySqlExe": "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe"
```

### 第三步：初始化专用演示数据库

```powershell
.\scripts\Initialize-Database.ps1
```

MySQL会提示输入**你安装MySQL时设置的本机管理员密码**。输入时不回显是正常现象。脚本不保存root密码，也不会删除已有数据库或重置已有账号密码。

初始化内容：

- 创建专用数据库`quickbiwin`（不存在时）。
- 创建本地迁移账号`qbwinmigrator`。
- 创建本地运行账号`qbwinportal`。
- 为localhost与127.0.0.1建立对应本地账号，不创建`%`远程账号。
- 建表、种子数据和运行账号表级权限由应用首次启动时的Flyway迁移执行。

**请只用于专用演示名称。**本脚本会为指定迁移账号授予该库DDL及GRANT权限，属于本地便利设计，不能针对业务生产库运行。

### 第四步：编译前端和后端

```powershell
.\scripts\Build-Demo.ps1
```

脚本顺序执行：

1. frontend中安装npm依赖；有锁文件时用npm ci，否则用npm install。
2. 执行Vue类型检查与Vite生产构建。
3. 将dist内容复制至backend/src/main/resources/static。
4. 执行Maven clean package，默认运行单元测试。
5. 生成backend/target/quickbi-demo.jar。

如仅临时排查编译问题，可显式跳过后端单测：

```powershell
.\scripts\Build-Demo.ps1 -SkipTests
```

跳过测试不等于测试通过。交付环境未生成npm锁文件，首次成功构建后请保留package-lock.json，以便后续使用npm ci复现。

### 第五步：启动

```powershell
.\scripts\Start-Demo.ps1
```

保持该窗口打开；等待后端启动完成后，在浏览器访问：

**`http://localhost:8081`**

健康检查：

```powershell
Invoke-RestMethod http://localhost:8081/healthz/ready
```

首次启动会创建业务表、演示用户和示例报表。日常再运行只需启动MySQL服务并执行Start-Demo.ps1，修改了源码则重新Build-Demo。

**停止：在运行窗口按Ctrl+C。**本脚本以前台方式运行，不注册Windows应用服务、不写PID文件、不执行广泛的java进程终止，也不会停止电脑上的其他Java应用或MySQL服务。

## 4. 开发方式：两个终端运行

先完成前面的环境安装和数据库初始化，不必先构建合并JAR。

**终端A：Java后端**

```powershell
cd C:\Projects\QuickBI-MySQL-Windows-Demo
.\scripts\Start-Backend.ps1
```

**终端B：Vue前端**

```powershell
cd C:\Projects\QuickBI-MySQL-Windows-Demo
.\scripts\Start-Frontend.ps1
```

浏览器访问 **`http://localhost:5173`**。Vite将/api、/auth、/mock和/healthz代理至后端，保留Origin检查和CSRF，不需要配置跨域放行。

开发模式请以5173作为入口；如果之前未合并打包，直接访问8081根路径不一定有前端页面。不要同时启动Start-Demo和Start-Backend，它们默认使用同一个8081端口。

在各自运行窗口按Ctrl+C停止。不要直接双击dist/index.html，也不要将Vite preview误当作配置了后端代理的完整站点。

## 5. local-settings.json配置

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| DbHost | 127.0.0.1 | 本Demo只允许localhost/127.0.0.1 |
| DbPort | 3306 | 本机MySQL端口 |
| Database | quickbiwin | 专用演示库；更改需重新初始化 |
| DbUser | qbwinportal | 应用运行账号 |
| DbPassword | Local-Portal-Demo-2026 | 公开本地演示密码 |
| MigrationUser | qbwinmigrator | Flyway迁移账号 |
| MigrationPassword | Local-Migrator-Demo-2026 | 公开本地演示密码 |
| MySqlExe | 空 | 自动查PATH和默认MySQL8.4安装位置；可配置完整路径 |
| ApiPort | 8081 | 合并JAR网页入口、JavaAPI端口 |
| FrontendPort | 5173 | 开发前端端口 |
| SessionGeneration | windows-local-generation-1 | 数据恢复后更换，以拒绝所有旧会话 |
| HmacKey | 本地示例值 | 至少32字符；不用于生产 |

启动脚本会显式设置DB_URL、DB_NAME、PORT、允许Origin等环境变量，避免继承旧终端中上一版端口/数据库设置。端口修改会同步反映在开发代理与允许Origin中，不需要手工改源码。

本地设置文件只含**公开演示凭据**，已加入gitignore。不要填生产密码。创建已有账号时CREATE USER IF NOT EXISTS不会修改原密码；更改配置密码后，需由管理员同步修改本机账号或使用新的专用演示用户名，不能认为重复初始化会替你改密码。

Flyway V3使用demoDatabase/demoRuntimeUser占位符授予运行账号权限；本地与测试库分别替换。默认双下划线迁移文件名已改为连字符，分隔符配置为`-`。

## 6. 不使用脚本时的手工命令

此方式使用默认配置；修改了local-settings.json端口/库名时建议使用脚本，以免遗漏相应环境变量。

### 6.1 MySQL手工初始化

用MySQL命令行客户端或Workbench以本机管理员连接，打开并执行`database/init.sql`。MySQL命令行中的SOURCE示例：

```sql
SOURCE C:/Projects/QuickBI-MySQL-Windows-Demo/database/init.sql;
```

这与系统PowerShell中的输入重定向不是一回事。本版不要求在PowerShell中使用不兼容的`<`命令。

### 6.2 分别编译

```powershell
cd frontend
npm.cmd install
npm.cmd run build
cd ..
New-Item -ItemType Directory -Force backend\src\main\resources\static
Copy-Item frontend\dist\* backend\src\main\resources\static -Recurse -Force
cd backend
mvn.cmd -B clean package
java.exe -jar target\quickbi-demo.jar
```

默认application.yml已适配本机3306/quickbiwin。如果未配置local-settings.json而直接启动JAR，这些就是默认值。推荐Build脚本在复制前清理旧静态资源，避免多次手工复制残留旧文件。

## 7. 演示账号与验证场景

| 账号 | 角色/映射 | 演示行为 |
| --- | --- | --- |
| Alice / 华东销售 | USER / bi-alice | 销售看板、经营概览 |
| Bob / 华南财务 | USER / bi-bob | 财务工作簿、经营概览 |
| 门户管理员 | ADMIN / 无BI映射 | 管理映射、同步、审计、配置、Mock实验区 |
| 只读审计员 | AUDITOR / 无BI映射 | 只读审计，不允许用户管理或同步 |
| 未映射用户 | USER / 无BI映射 | 显示关联帮助，不授予BI访问 |

Mock登录是选择本地身份，不是Entra认证。用两个浏览器或普通窗口+隐私窗口测试不同账号，不能指望同一浏览器的两个标签页拥有独立Cookie会话。

建议顺序：Alice查看经营概览→管理员另一个窗口撤销Alice的销售报表Mock授权→Alice点击旧列表应404→恢复授权→管理员保存Alice映射应使Alice下次请求401。完整步骤见`docs/Demo-Guide.md`。

## 8. 不依赖容器的测试

### 8.1 Java工具自测

```powershell
.\scripts\Test-Security.ps1
```

仅需JDK，覆盖HMAC、比较、HTML编码和随机值基本检查。

### 8.2 Maven单测

```powershell
cd backend
mvn.cmd test
cd ..
```

### 8.3 原生MySQL集成测试

首次创建**独立测试库/账号**：

```powershell
.\scripts\Initialize-Database.ps1 -ForTests
.\scripts\Test-MySql.ps1
```

默认测试库quickbiwinit，运行账号qbwinportalit，迁移账号qbwinmigratorit。测试连接由TEST_DB环境变量显式传入，缺少配置或不是本机测试库时失败，**不会自动回退到演示业务库**。

它运行mvn -Pmysql-it verify，校验迁移、多个NULL映射、BI目标唯一性、大小写比较、审计运行账号不可更新删除、活动FULL去重。测试不会创建临时数据库服务，而是使用已启动的本机MySQL。没有初始化测试库就执行会报错，不会跳过并宣称通过。

### 8.4 HTTP冒烟测试（可选Python）

启动合并JAR后：

```powershell
.\scripts\Test-Http.ps1
```

开发模式：

```powershell
.\scripts\Test-Http.ps1 -Development
```

脚本自动选py.exe/python.exe，运行Python标准库HTTP测试，不安装Python包。会更新Mock授权、配置版本、Alice映射版本并撤销会话，请使用专用演示数据。实际输出PASS才算通过。

## 9. 常见问题

| 现象 | 排查与解决 |
| --- | --- |
| 禁止执行.ps1 | 先审核脚本。公司允许时可在当前会话设RemoteSigned；组策略阻止则联系IT，不绕过策略。也可按第6节手工运行 |
| npm.ps1被策略阻止 | 本版脚本调用npm.cmd；手工命令也使用npm.cmd |
| 找不到mvn/java/node | 正确安装并配置PATH/JAVA_HOME，重新打开终端 |
| 3306连接失败 | 安装的是Server还是只有Workbench？MySQL服务是否运行？local-settings端口是否与服务一致？ |
| mysql.exe路径有空格 | 在MySqlExe中配置完整路径；脚本用调用运算符处理，不要手工拼接未引用路径 |
| MySQL Access denied | 初始化密码是本机root密码；运行密码来自local-settings。旧账号不会自动改密码 |
| 不存在quickbiwin库 | 先执行Initialize-Database，启动应用才会执行建表迁移 |
| 8081被占用 | 停止旧Demo或改ApiPort；不要同时启动合并模式和开发后端 |
| 8081根路径404 | 没有合并前端；执行Build-Demo，或开发模式访问5173 |
| 前端502 | 检查后端是否ready，ApiPort与脚本生成的Vite代理是否一致 |
| 脚本提示版本不满足 | 用JDK21与Node22.12+；JRE不能代替JDK |
| Flyway checksum不符 | 不修改已经执行的SQL；这是本版独立演示库，不应接旧版库迁移历史 |
| 修改映射后401 | 预期会话撤销，重新登录 |
| 旧列表仍能看到撤权报表 | TTL内列表可以旧，但新签票必须404；可等待TTL或手工用户同步 |
| Mock票据再次打开410 | 单次票据已消费，回门户重新打开 |
| 重复请求409/过多请求429 | 不重放票据；用显式新打开生成新键，限流时等一分钟 |

### 仅在公司允许时处理执行策略

审核本地脚本后，当前PowerShell会话可使用：

```powershell
Get-ExecutionPolicy -List
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
```

若从下载的ZIP解压后文件带有网络来源标记，可在审核文件后对本项目脚本单独使用Unblock-File；不要对陌生下载内容批量解除限制。Process设置只在当前进程有效，不建议修改整机策略，也不自动使用Bypass。企业组策略优先，应联系IT。

## 10. 安全和生产差异

本地HTTP Cookie没有Secure标记，JDBC为本机开发关闭TLS并允许公钥检索，数据库密码为公开演示值；应用只监听127.0.0.1。**这不是生产部署配置。**

生产必须删除Mock登录/授权/故障入口，接入真实OIDC/PKCE与Quick BI SDK，HTTPS/Secure Cookie、VERIFY_IDENTITY+受控CA、Secrets、独立Worker与迁移、审计可靠归档、受控留存、完整G01～G06验收。本版没有实现S3/outbox、生产会话框架扩展、每日02:00调度、性能承诺或Windows服务化部署。

## 11. 目录与官方参考

```text
QuickBI-MySQL-Windows-Demo/
  local-settings.example.json
  README.md
  database/init.sql
  scripts/
    Check-Environment.ps1
    Initialize-Database.ps1
    Build-Demo.ps1
    Start-Demo.ps1
    Start-Backend.ps1
    Start-Frontend.ps1
    Test-Security.ps1
    Test-MySql.ps1
    Test-Http.ps1
    Common.ps1
    SecuritySelfTest.java
    smoke.py
  backend/                     Java源码、Maven、MySQL迁移与测试
  frontend/                    Vue/TypeScript源码
  docs/                        接口、架构、演示与验证记录
```

本次Windows实现参考：

- MySQL Windows服务：`https://dev.mysql.com/doc/refman/8.4/en/windows-start-service.html`
- Maven安装/PATH：`https://maven.apache.org/install.html`
- Windows Java环境：`https://learn.microsoft.com/en-us/windows/dev-environment/java`
- Spring Boot classpath static资源：`https://docs.spring.io/spring-boot/reference/web/servlet.html`
- PowerShell执行策略：`https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_execution_policies`

这些文档解释能力和配置，不代替本项目的Windows构建实测。

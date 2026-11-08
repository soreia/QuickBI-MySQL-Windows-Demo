# Windows原生改造清单

| 领域 | 本版变化 |
| --- | --- |
| 运行 | 删除容器定义和Nginx配置，不再要求额外Web服务器 |
| 默认连接 | 127.0.0.1:3306/quickbiwin；上一版演示库不复用 |
| 本地配置 | local-settings.json；脚本读取JSON并显式设置运行环境，不执行配置文本 |
| 数据库初始化 | PowerShell交互调用mysql.exe，root密码不保存；仅本地账号，不用%远程主机 |
| 编译 | npm前端构建→复制classpath static→Maven打成单JAR |
| 启动 | Start-Demo前台单JAR，Ctrl+C停止；开发模式另有前后端两个入口 |
| 静态安全头 | 原Web层安全头移入Java过滤器，合并JAR直接返回 |
| 集成测试 | 移除Testcontainers，改为显式初始化的原生MySQL隔离测试库 |
| 迁移授权 | V3占位符指向本地/测试各自数据库及账号 |
| 文档 | Windows安装、PATH、执行策略、MySQL服务、端口及本地排错 |

功能仍为本地Mock范围；不新增真实SSO、真实Quick BI或生产验收承诺。所有脚本源码可审阅，不自动安装软件、改系统策略、提权或批量终止Java进程。

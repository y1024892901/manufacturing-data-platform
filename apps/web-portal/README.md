# 制造业运营门户（Vue 3）

这是演示系统的统一前端入口，承载登录、按岗位展示的十系统工作台、待办审批和主数据维护；后续 P2-P4 的 CRM、ERP、MES
等页面也在此目录持续增加。

技术栈：Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios。

在本目录执行 `pnpm install`，再执行 `pnpm dev`。`/api` 会转发至 Java 后端 `http://localhost:8080`。账号由后端动态读取；密码不写进前端代码。

package com.mfg.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 源系统启动入口。
 *
 * <p><b>单进程多模块</b>：MDM（统一主数据平台）与 9 类业务系统共用一个 Spring 容器、
 * 一个端口（8080），通过 URL 前缀区分：{@code /api/mdm/**}、{@code /api/erp/**} ...
 *
 * <p>为什么不做成 10 个独立进程：
 * <ul>
 *   <li>10 个 JVM 约需 3~4GB 内存，演示机吃力</li>
 *   <li>启动一次即可，演示时不用等 10 个服务依次就绪</li>
 *   <li>需求是"每个系统数据能独立维护"——这是<b>数据和界面</b>的独立，
 *       不是进程的独立。10 个模块各有自己的库、自己的 Controller、自己的权限点</li>
 * </ul>
 *
 * <p>扫描 {@code com.mfg} 下的全部模块，因此各业务模块的 Controller/Service
 * 会被自动注册，无需在启动类里逐个声明。
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.mfg")
@EntityScan(basePackages = "com.mfg")
@EnableJpaRepositories(basePackages = "com.mfg")
public class MfgSourceApplication {

    public static void main(String[] args) {
        loadLocalEnv();
        ConfigurableApplicationContext ctx = SpringApplication.run(MfgSourceApplication.class, args);
        printStartupBanner(ctx.getEnvironment());
    }

    /**
     * 支持直接从 IDEA 运行。
     *
     * <p>Spring Boot 不会自动解析 .env；此前只有 run.bat 会把
     * infra/.env 注入进程环境，导致 IDEA 直接运行时容易使用过期密码。
     * 本方法从工作目录和编译输出目录两条路径寻找项目根目录的 infra/.env，
     * 并把其中的本地配置写入 JVM 系统属性。系统属性优先于 IDEA 运行配置里的
     * 同名环境变量，因此不会被遗留的 MYSQL_PASSWORD 覆盖。该文件已被 Git
     * 忽略，不会进入源码库。</p>
     */
    private static void loadLocalEnv() {
        for (Path start : findEnvSearchStarts()) {
            Path cursor = start;
            while (cursor != null) {
                Path envFile = cursor.resolve("infra").resolve(".env");
            if (Files.isRegularFile(envFile)) {
                try {
                    List<String> lines = Files.readAllLines(envFile);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        int separator = trimmed.indexOf('=');
                        if (separator <= 0) {
                            continue;
                        }
                        String key = trimmed.substring(0, separator).trim();
                        String value = trimmed.substring(separator + 1);
                        // .env 是本地演示环境的唯一事实来源。System Property 优先级
                        // 高于 IDEA Run Configuration 的 Environment Variables，可屏蔽遗留口令。
                        System.setProperty(key, value);
                        if ("MYSQL_PASSWORD".equals(key)) {
                            System.setProperty("spring.datasource.password", value);
                        }
                        if ("JWT_SECRET".equals(key)) {
                            System.setProperty("mfg.jwt.secret", value);
                        }
                    }
                    log.info("已加载本地环境文件: {}", envFile);
                } catch (IOException e) {
                    log.warn("读取本地环境文件失败: {}", envFile, e);
                }
                return;
            }
            cursor = cursor.getParent();
        }
        }
        log.warn("未发现 infra/.env；请在 IDEA Run Configuration 配置 MYSQL_PASSWORD 与 JWT_SECRET");
    }

    private static List<Path> findEnvSearchStarts() {
        List<Path> starts = new ArrayList<>();
        starts.add(Path.of("").toAbsolutePath());
        try {
            CodeSource codeSource = MfgSourceApplication.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                starts.add(Path.of(codeSource.getLocation().toURI()).toAbsolutePath());
            }
        } catch (Exception ignored) {
            // 工作目录仍可作为正常回退路径。
        }
        return starts;
    }

    private static void printStartupBanner(Environment env) {
        String port = env.getProperty("server.port", "8080");
        log.info("""

                ============================================================
                  制造业数据平台 —— 源系统已启动
                ============================================================
                  服务地址    http://localhost:{}

                  接口文档    http://localhost:{}/swagger-ui.html
                  演示账号    GET  /api/auth/demo-accounts
                  健康检查    GET  /api/health

                  ┌─ 统一主数据管理平台 ─────────────────────────────┐
                  │  /api/mdm/**      12 类主数据 + 审批 + 分发      │
                  └──────────────────────────────────────────────────┘
                  ┌─ 9 类业务系统 ───────────────────────────────────┐
                  │  /api/crm/**      客户商机                       │
                  │  /api/erp/**      订单 · 凭证 · 应收             │
                  │  /api/mes/**      工单 · 报工                    │
                  │  /api/wms/**      库存 · 出入库                  │
                  │  /api/eam/**      设备 · 故障 · 点检             │
                  │  /api/qms/**      检验 · 不合格品                │
                  │  /api/srm/**      采购 · 到货                    │
                  │  /api/plm/**      产品 · BOM · 工程变更          │
                  │  /api/energy/**   能耗                           │
                  └──────────────────────────────────────────────────┘
                  ┌─ 跨系统能力 ─────────────────────────────────────┐
                  │  /api/auth/**     登录 · 身份切换                 │
                  │  /api/workflow/** 待办 · 审批                    │
                  └──────────────────────────────────────────────────┘
                ============================================================
                """, port, port);
    }
}

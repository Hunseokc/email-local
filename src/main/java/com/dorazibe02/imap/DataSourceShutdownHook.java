package com.dorazibe02.imap;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataSourceShutdownHook {

    private final DataSource dataSource;

    public DataSourceShutdownHook(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PreDestroy
    public void closeDataSource() throws Exception {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDataSource) {
            hikariDataSource.close(); // 명시적으로 close()
            System.out.println("HikariDataSource closed successfully.");
        }
    }
}

// 작동 안하면
//SELECT pg_terminate_backend(pid)
//FROM pg_stat_activity
//WHERE datname = 'postgres'  -- 또는 실제 DB 이름
//AND pid <> pg_backend_pid();

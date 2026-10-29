package com.pms.propertymanagement.config.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(0)
@Slf4j
public class MaintenanceSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            var conn = jdbcTemplate.getDataSource().getConnection();
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            conn.close();
            if (dbProduct != null && !dbProduct.toLowerCase().contains("sql server") && !dbProduct.toLowerCase().contains("microsoft")) {
                // Hibernate ddl-auto=update automatically creates/updates MySQL schema from entities
                return;
            }
        } catch (Exception e) {
            return;
        }

        ensureMaintenanceRequestsCodePopulated();
        ensureMaintenanceRequestsHasTenantId();
        ensureMaintenanceRequestsHasStaffWorkflowColumns();
        ensureMaintenanceRequestsCategoryConstraintAndData();
        ensureMaintenanceRequestsStatusConstraint();
        ensureMaintenanceRequestsRequesterIdDoesNotBlockInserts();
        ensureMaintenanceRequestsDropPriority();
        ensurePostsStatusConstraintIncludesPendingRevision();
    }

    private void ensureMaintenanceRequestsHasTenantId() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'tenant_id'",
                Integer.class
        );
        if (columnCount != null && columnCount > 0) {
            return;
        }

        log.info("Đang cập nhật schema: thêm cột tenant_id vào bảng maintenance_requests");
        jdbcTemplate.execute("ALTER TABLE maintenance_requests ADD tenant_id BIGINT NULL");

        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.foreign_keys WHERE name = 'FK_maintenance_requests_tenant'",
                Integer.class
        );
        if (fkCount != null && fkCount == 0) {
            log.info("Đang cập nhật schema: thêm FK FK_maintenance_requests_tenant");
            jdbcTemplate.execute(
                    "ALTER TABLE maintenance_requests WITH NOCHECK " +
                            "ADD CONSTRAINT FK_maintenance_requests_tenant " +
                            "FOREIGN KEY (tenant_id) REFERENCES users(id)"
            );
        }
    }

    private void ensureMaintenanceRequestsCodePopulated() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'code'",
                Integer.class
        );
        if (columnCount == null || columnCount == 0) {
            return;
        }

        jdbcTemplate.execute(
                "UPDATE maintenance_requests " +
                        "SET code = CONCAT('MR-', FORMAT(COALESCE(created_at, GETDATE()), 'yyyyMMddHHmmss'), '-', id) " +
                        "WHERE code IS NULL OR LTRIM(RTRIM(code)) = ''"
        );
    }

    private void ensureMaintenanceRequestsHasStaffWorkflowColumns() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        ensureColumnExists("staff_id", "BIGINT NULL");
        ensureColumnExists("assigned_at", "DATETIME2 NULL");
        ensureColumnExists("started_at", "DATETIME2 NULL");
        ensureColumnExists("completed_at", "DATETIME2 NULL");
        ensureColumnExists("rejected_reason", "NVARCHAR(MAX) NULL");
        ensureColumnExists("completion_note", "NVARCHAR(MAX) NULL");

        Integer fkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.foreign_keys WHERE name = 'FK_maintenance_requests_staff'",
                Integer.class
        );
        if (fkCount != null && fkCount == 0) {
            jdbcTemplate.execute(
                    "ALTER TABLE maintenance_requests WITH NOCHECK " +
                            "ADD CONSTRAINT FK_maintenance_requests_staff " +
                            "FOREIGN KEY (staff_id) REFERENCES users(id)"
            );
        }
    }

    private void ensureMaintenanceRequestsCategoryConstraintAndData() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'category'",
                Integer.class
        );
        if (columnCount == null || columnCount == 0) {
            return;
        }

        jdbcTemplate.queryForList(
                "SELECT cc.name " +
                        "FROM sys.check_constraints cc " +
                        "JOIN sys.tables t ON t.object_id = cc.parent_object_id " +
                        "WHERE t.name = 'maintenance_requests' AND cc.definition LIKE '%category%'",
                String.class
        ).forEach(name -> jdbcTemplate.execute("ALTER TABLE maintenance_requests DROP CONSTRAINT [" + name + "]"));

        jdbcTemplate.execute(
                "UPDATE maintenance_requests " +
                        "SET category = CASE LTRIM(RTRIM(category)) " +
                        "WHEN N'Điện' THEN 'ELECTRICAL' " +
                        "WHEN N'Nước' THEN 'PLUMBING' " +
                        "WHEN N'Điều hòa' THEN 'AIRCON' " +
                        "WHEN N'Nội thất' THEN 'FURNITURE' " +
                        "WHEN N'Khác' THEN 'OTHER' " +
                        "ELSE category END " +
                        "WHERE category IS NOT NULL"
        );

        jdbcTemplate.execute(
                "UPDATE maintenance_requests " +
                        "SET category = 'OTHER' " +
                        "WHERE category IS NULL " +
                        "OR LTRIM(RTRIM(category)) = '' " +
                        "OR category NOT IN ('ELECTRICAL','PLUMBING','AIRCON','FURNITURE','OTHER')"
        );

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'CK_maintenance_requests_category'",
                Integer.class
        );
        if (exists != null && exists > 0) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE maintenance_requests " +
                        "ADD CONSTRAINT CK_maintenance_requests_category " +
                        "CHECK (category IN ('ELECTRICAL','PLUMBING','AIRCON','FURNITURE','OTHER'))"
        );
    }

    private void ensureMaintenanceRequestsStatusConstraint() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        jdbcTemplate.queryForList(
                "SELECT cc.name " +
                        "FROM sys.check_constraints cc " +
                        "JOIN sys.tables t ON t.object_id = cc.parent_object_id " +
                        "WHERE t.name = 'maintenance_requests' AND cc.definition LIKE '%status%'",
                String.class
        ).forEach(name -> jdbcTemplate.execute("ALTER TABLE maintenance_requests DROP CONSTRAINT [" + name + "]"));

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.check_constraints WHERE name = 'CK_maintenance_requests_status'",
                Integer.class
        );
        if (exists != null && exists > 0) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE maintenance_requests " +
                        "ADD CONSTRAINT CK_maintenance_requests_status " +
                        "CHECK (status IN ('PENDING','ASSIGNED','IN_PROGRESS','COMPLETED','CONFIRMED','REJECTED','REOPENED'))"
        );
    }

    private void ensureColumnExists(String columnName, String columnDefinition) {
        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
        );
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE maintenance_requests ADD " + columnName + " " + columnDefinition);
    }

    private void ensureMaintenanceRequestsDropPriority() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'priority'",
                Integer.class
        );
        if (columnCount == null || columnCount == 0) {
            return;
        }

        log.info("Đang cập nhật schema: xóa cột priority khỏi bảng maintenance_requests");

        jdbcTemplate.queryForList(
                "SELECT dc.name " +
                        "FROM sys.default_constraints dc " +
                        "JOIN sys.columns c ON c.default_object_id = dc.object_id " +
                        "JOIN sys.tables t ON t.object_id = c.object_id " +
                        "WHERE t.name = 'maintenance_requests' AND c.name = 'priority'",
                String.class
        ).forEach(name -> jdbcTemplate.execute("ALTER TABLE maintenance_requests DROP CONSTRAINT [" + name + "]"));

        jdbcTemplate.queryForList(
                "SELECT cc.name " +
                        "FROM sys.check_constraints cc " +
                        "JOIN sys.tables t ON t.object_id = cc.parent_object_id " +
                        "WHERE t.name = 'maintenance_requests' AND cc.definition LIKE '%priority%'",
                String.class
        ).forEach(name -> jdbcTemplate.execute("ALTER TABLE maintenance_requests DROP CONSTRAINT [" + name + "]"));

        jdbcTemplate.queryForList(
                "SELECT i.name " +
                        "FROM sys.indexes i " +
                        "JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id " +
                        "JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id " +
                        "JOIN sys.tables t ON t.object_id = i.object_id " +
                        "WHERE t.name = 'maintenance_requests' AND c.name = 'priority' " +
                        "AND i.is_primary_key = 0 AND i.is_unique_constraint = 0 AND i.name IS NOT NULL",
                String.class
        ).forEach(name -> jdbcTemplate.execute("DROP INDEX [" + name + "] ON maintenance_requests"));

        jdbcTemplate.execute("ALTER TABLE maintenance_requests DROP COLUMN priority");
    }

    private void ensureMaintenanceRequestsRequesterIdDoesNotBlockInserts() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'maintenance_requests'",
                Integer.class
        );
        if (tableCount == null || tableCount == 0) {
            return;
        }

        Integer requesterColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'requester_id'",
                Integer.class
        );
        if (requesterColumnCount == null || requesterColumnCount == 0) {
            return;
        }

        String requesterNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'requester_id'",
                String.class
        );
        if ("NO".equalsIgnoreCase(requesterNullable)) {
            log.info("Đang cập nhật schema: cho phép NULL cho cột requester_id trong bảng maintenance_requests");
            jdbcTemplate.execute("ALTER TABLE maintenance_requests ALTER COLUMN requester_id BIGINT NULL");
        }

        Integer tenantColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'maintenance_requests' AND COLUMN_NAME = 'tenant_id'",
                Integer.class
        );
        if (tenantColumnCount != null && tenantColumnCount > 0) {
            jdbcTemplate.execute(
                    "UPDATE maintenance_requests SET tenant_id = requester_id " +
                            "WHERE tenant_id IS NULL AND requester_id IS NOT NULL"
            );
        }
    }

    private void ensurePostsStatusConstraintIncludesPendingRevision() {
        Integer alreadyOk = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys.check_constraints cc " +
                "JOIN sys.objects o ON cc.parent_object_id = o.object_id " +
                "WHERE o.name = 'posts' AND cc.definition LIKE '%PENDING_REVISION%'",
                Integer.class
        );
        if (alreadyOk != null && alreadyOk > 0) {
            return;
        }

        try {
            String dropSql =
                "DECLARE @sql NVARCHAR(MAX) = ''; " +
                "SELECT @sql = @sql + 'ALTER TABLE posts DROP CONSTRAINT ' + cc.name + '; ' " +
                "FROM sys.check_constraints cc " +
                "JOIN sys.objects o   ON cc.parent_object_id = o.object_id " +
                "JOIN sys.columns col ON cc.parent_object_id = col.object_id " +
                "                    AND cc.parent_column_id = col.column_id " +
                "WHERE o.name = 'posts' AND col.name = 'status'; " +
                "EXEC sp_executesql @sql;";
            jdbcTemplate.execute(dropSql);
            log.info("[Schema] Dropped old posts.status CHECK constraint(s).");
        } catch (Exception e) {
            log.warn("[Schema] Could not drop posts.status constraint (may not exist): {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                "ALTER TABLE posts ADD CONSTRAINT CK_posts_status_v2 " +
                "CHECK (status IN (" +
                "'PENDING_APPROVAL','PENDING_REVISION','REJECTED'," +
                "'DRAFT','ACTIVE','EXPIRED','HIDDEN'))"
            );
            log.info("[Schema] Created new posts.status CHECK constraint with PENDING_REVISION.");
        } catch (Exception e) {
            log.warn("[Schema] Could not create posts.status constraint: {}", e.getMessage());
        }
    }
}

package com.onclick.domain.sale.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleSchemaMigrationTest {

    @Test
    void migratesLegacySaleLinesIntoTransactionHeadersAndItems() {
        DataSource dataSource = dataSource();
        migrateTo(dataSource, "1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedLegacySales(jdbc);

        migrateTo(dataSource, "3");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_transactions", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sale_items", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sale_transactions WHERE client_transaction_id = 'POS-1'",
                String.class
        )).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sale_transactions WHERE client_transaction_id = 'POS-2'",
                String.class
        )).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                """
                SELECT SUM(item.paid_amount)
                FROM sale_items item
                JOIN sale_transactions st
                  ON st.id = item.sale_transaction_id
                WHERE st.client_transaction_id = 'POS-1'
                """,
                Long.class
        )).isEqualTo(13_500L);
        assertThat(tableCount(jdbc, "sales")).isZero();
        assertThat(tableCount(jdbc, "hourly_visitor_counts")).isZero();
    }

    @Test
    void rejectsStoreWithMultipleLegacyOwners() {
        DataSource dataSource = dataSource();
        migrateTo(dataSource, "1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedLegacySales(jdbc);
        Instant createdAt = Instant.parse("2026-07-13T03:00:00Z");
        jdbc.update(
                """
                INSERT INTO users (id, account_id, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                2L,
                "second-owner",
                "password-hash",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        jdbc.update(
                """
                INSERT INTO user_store_memberships (id, user_id, store_id, role, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                12L,
                2L,
                10L,
                "OWNER",
                Timestamp.from(createdAt)
        );

        assertThatThrownBy(() -> migrateTo(dataSource, "2"))
                .hasMessageContaining("V2__replace_memberships_with_store_owner.sql");
    }

    @Test
    void rejectsLegacyTransactionWithMixedLineStatuses() {
        DataSource dataSource = dataSource();
        migrateTo(dataSource, "1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedLegacySales(jdbc);
        jdbc.update(
                """
                UPDATE sales
                SET status = 'CANCELLED', cancelled_at = ?
                WHERE id = 31
                """,
                Timestamp.from(Instant.parse("2026-07-13T05:00:00Z"))
        );

        assertThatThrownBy(() -> migrateTo(dataSource, "3"))
                .hasMessageContaining("V3__split_sale_transactions_and_remove_hourly_visitors.sql");
    }

    @Test
    void migratesExistingAccountsAndStoresToWorkflowSchema() {
        DataSource dataSource = dataSource();
        migrateTo(dataSource, "1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedLegacySales(jdbc);

        migrateTo(dataSource, "4");

        assertThat(jdbc.queryForObject(
                "SELECT closing_time FROM stores WHERE id = 10",
                LocalTime.class
        )).isEqualTo(LocalTime.of(22, 0));
        assertThat(jdbc.queryForObject(
                "SELECT name FROM users WHERE id = 1",
                String.class
        )).isNull();
        assertThat(tableCount(jdbc, "consultings")).isEqualTo(1);
        assertThat(tableCount(jdbc, "chat_rooms")).isEqualTo(1);
        assertThat(tableCount(jdbc, "chat_messages")).isEqualTo(1);
        assertThat(tableCount(jdbc, "media_files")).isEqualTo(1);
        assertThat(tableCount(jdbc, "marketing_contents")).isEqualTo(1);
        assertThat(tableCount(jdbc, "instagram_integrations")).isEqualTo(1);
    }

    private void seedLegacySales(JdbcTemplate jdbc) {
        Instant createdAt = Instant.parse("2026-07-13T03:00:00Z");
        jdbc.update(
                """
                INSERT INTO users (id, account_id, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                1L,
                "migration-owner",
                "password-hash",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        jdbc.update(
                """
                INSERT INTO stores (id, name, time_zone, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                10L,
                "이관 매장",
                "Asia/Seoul",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        jdbc.update(
                """
                INSERT INTO user_store_memberships (id, user_id, store_id, role, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                11L,
                1L,
                10L,
                "OWNER",
                Timestamp.from(createdAt)
        );
        jdbc.update(
                """
                INSERT INTO products (id, store_id, name, price, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                20L,
                10L,
                "기존 상품",
                4_500L,
                true,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );

        insertLegacySale(jdbc, 30L, "POS-1", 1, 2, 9_000L, "COMPLETED", null);
        insertLegacySale(jdbc, 31L, "POS-1", 2, 1, 4_500L, "COMPLETED", null);
        insertLegacySale(
                jdbc,
                32L,
                "POS-2",
                1,
                1,
                4_500L,
                "CANCELLED",
                Instant.parse("2026-07-13T05:00:00Z")
        );
    }

    private void insertLegacySale(
            JdbcTemplate jdbc,
            Long id,
            String transactionId,
            int lineNo,
            int quantity,
            long paidAmount,
            String status,
            Instant cancelledAt
    ) {
        Instant soldAt = Instant.parse("2026-07-13T04:00:00Z");
        Instant createdAt = Instant.parse("2026-07-13T04:01:00Z");
        jdbc.update(
                """
                INSERT INTO sales (
                    id, store_id, transaction_id, line_no, product_id,
                    product_name_snapshot, product_price_snapshot, quantity,
                    paid_amount, sold_at, status, cancelled_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                10L,
                transactionId,
                lineNo,
                20L,
                "기존 상품",
                4_500L,
                quantity,
                paidAmount,
                Timestamp.from(soldAt),
                status,
                cancelledAt == null ? null : Timestamp.from(cancelledAt),
                Timestamp.from(createdAt)
        );
    }

    private void migrateTo(DataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private int tableCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE LOWER(table_name) = ?
                """,
                Integer.class,
                tableName
        );
    }

    private DataSource dataSource() {
        String databaseName = "sale_migration_" + UUID.randomUUID().toString().replace("-", "");
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
    }
}

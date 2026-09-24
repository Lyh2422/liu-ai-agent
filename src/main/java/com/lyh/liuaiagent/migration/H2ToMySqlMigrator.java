package com.lyh.liuaiagent.migration;

import org.flywaydb.core.Flyway;

import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * 一次性将旧 H2 业务数据迁移到空的 MySQL schema。
 * 该工具只读 H2，在 MySQL 中使用单个事务写入，不会删除或修改源数据。
 */
public final class H2ToMySqlMigrator {
    private static final List<String> TABLES = List.of(
            "user_accounts",
            "chat_conversations",
            "chat_turns",
            "conversation_memories",
            "knowledge_documents",
            "friendships",
            "social_chat_rooms",
            "social_chat_members",
            "social_chat_messages",
            "user_memory_facts"
    );

    private H2ToMySqlMigrator() {}

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.fromEnvironment();
        Flyway.configure()
                .dataSource(settings.targetUrl(), settings.targetUser(), settings.targetPassword())
                .locations("classpath:db/migration/mysql")
                .load()
                .migrate();

        try (Connection source = DriverManager.getConnection(
                    settings.sourceUrl(), settings.sourceUser(), settings.sourcePassword());
             Connection target = DriverManager.getConnection(
                    settings.targetUrl(), settings.targetUser(), settings.targetPassword())) {
            source.setReadOnly(true);
            source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            source.setAutoCommit(false);
            target.setAutoCommit(false);
            target.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            try {
                assertTargetEmpty(target);
                Map<String, Long> sourceCounts = new LinkedHashMap<>();
                Map<String, Long> targetCounts = new LinkedHashMap<>();
                for (String table : TABLES) {
                    if (!tableExists(source, table)) continue;
                    long sourceCount = count(source, table);
                    sourceCounts.put(table, sourceCount);
                    copyTable(source, target, table);
                    long targetCount = count(target, table);
                    targetCounts.put(table, targetCount);
                    if (sourceCount != targetCount) {
                        throw new SQLException("Count mismatch for " + table + ": source="
                                + sourceCount + ", target=" + targetCount);
                    }
                }
                target.commit();
                source.rollback();
                printSummary(sourceCounts, targetCounts);
            } catch (Exception error) {
                target.rollback();
                source.rollback();
                throw error;
            }
        }
    }

    private static void assertTargetEmpty(Connection target) throws SQLException {
        List<String> nonEmpty = new ArrayList<>();
        for (String table : TABLES) {
            if (tableExists(target, table) && count(target, table) > 0) nonEmpty.add(table);
        }
        if (!nonEmpty.isEmpty()) {
            throw new IllegalStateException("Target MySQL is not empty; refusing to merge into tables: "
                    + String.join(", ", nonEmpty));
        }
    }

    private static void copyTable(Connection source, Connection target, String table) throws SQLException {
        List<String> sourceColumns = columns(source, table);
        Set<String> targetColumns = new LinkedHashSet<>(columns(target, table));
        List<String> columns = sourceColumns.stream().filter(targetColumns::contains).toList();
        if (columns.isEmpty()) throw new SQLException("No common columns for table " + table);

        String selected = columns.stream().map(H2ToMySqlMigrator::h2Quote)
                .reduce((left, right) -> left + "," + right).orElseThrow();
        String inserted = columns.stream().map(H2ToMySqlMigrator::mysqlQuote)
                .reduce((left, right) -> left + "," + right).orElseThrow();
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String selectSql = "SELECT " + selected + " FROM " + h2Quote(table);
        String insertSql = "INSERT INTO " + mysqlQuote(table) + " (" + inserted + ") VALUES (" + placeholders + ")";

        try (Statement query = source.createStatement();
             ResultSet rows = query.executeQuery(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            int pending = 0;
            while (rows.next()) {
                for (int index = 0; index < columns.size(); index++) {
                    setValue(insert, index + 1, rows.getObject(index + 1));
                }
                insert.addBatch();
                pending++;
                if (pending == 500) {
                    insert.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) insert.executeBatch();
        }
    }

    private static void setValue(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value instanceof Clob clob) {
            statement.setString(index, clob.getSubString(1, Math.toIntExact(clob.length())));
        } else if (value instanceof OffsetDateTime time) {
            statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.ofInstant(time.toInstant(), ZoneOffset.UTC)));
        } else if (value instanceof ZonedDateTime time) {
            statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.ofInstant(time.toInstant(), ZoneOffset.UTC)));
        } else if (value instanceof Instant time) {
            statement.setTimestamp(index, Timestamp.valueOf(LocalDateTime.ofInstant(time, ZoneOffset.UTC)));
        } else if (value instanceof LocalDateTime time) {
            statement.setTimestamp(index, Timestamp.valueOf(time));
        } else {
            statement.setObject(index, value);
        }
    }

    private static List<String> columns(Connection connection, String table) throws SQLException {
        String quote = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2")
                ? h2Quote(table) : mysqlQuote(table);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM " + quote + " WHERE 1 = 0")) {
            ResultSetMetaData metadata = result.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(metadata.getColumnName(index).toLowerCase(Locale.ROOT));
            }
            return columns;
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT))) {
            try (ResultSet result = metadata.getTables(connection.getCatalog(), null, candidate, new String[]{"TABLE"})) {
                if (result.next()) return true;
            }
        }
        return false;
    }

    private static long count(Connection connection, String table) throws SQLException {
        boolean h2 = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("H2");
        String quoted = h2 ? h2Quote(table) : mysqlQuote(table);
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + quoted)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static String h2Quote(String identifier) {
        return '"' + identifier.toUpperCase(Locale.ROOT).replace("\"", "\"\"") + '"';
    }

    private static String mysqlQuote(String identifier) {
        return '`' + identifier.replace("`", "``") + '`';
    }

    private static void printSummary(Map<String, Long> source, Map<String, Long> target) {
        System.out.println("H2 -> MySQL migration completed and verified:");
        source.forEach((table, count) -> System.out.printf("  %-26s %d -> %d%n", table, count, target.get(table)));
    }

    private record Settings(String sourceUrl, String sourceUser, String sourcePassword,
                            String targetUrl, String targetUser, String targetPassword) {
        static Settings fromEnvironment() {
            return new Settings(
                    environment("SOURCE_H2_URL", "jdbc:h2:file:./tmp/data/liu-ai-agent;MODE=MySQL;AUTO_SERVER=TRUE"),
                    environment("SOURCE_H2_USERNAME", "root"),
                    environment("SOURCE_H2_PASSWORD", ""),
                    environment("TARGET_MYSQL_URL", "jdbc:mysql://localhost:3306/liu_ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"),
                    environment("TARGET_MYSQL_USERNAME", "liu_ai_agent"),
                    environment("TARGET_MYSQL_PASSWORD", "liu_ai_agent_dev")
            );
        }

        private static String environment(String name, String fallback) {
            String value = System.getenv(name);
            return value == null ? fallback : value;
        }
    }
}

package demo;
import org.junit.jupiter.api.*;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

// Native LOCAL MySQL, no container runtime. Use a separate database initialized with -ForTests.
class DatabaseIT {
    private static String url;
    private static String required(String key) {
        String value=System.getenv(key);
        if(value==null || value.isBlank()) throw new IllegalStateException("Missing "+key+". Run scripts/Test-MySql.ps1 after Initialize-Database.ps1 -ForTests.");
        return value;
    }
    @BeforeAll static void migrate() {
        url=required("TEST_DB_URL");
        String schema=required("TEST_DB_NAME"),runtimeUser=required("TEST_DB_USER");
        if(!schema.matches("[a-z][a-z0-9]{1,29}it") || !runtimeUser.matches("[a-z][a-z0-9]{1,29}it"))
            throw new IllegalStateException("IT requires isolated local database/user names ending in it.");
        if(!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/"+schema+"[?].*"))
            throw new IllegalStateException("IT may only connect to its named loopback test database.");
        Flyway.configure().dataSource(url,required("TEST_MIGRATION_USER"),required("TEST_MIGRATION_PASSWORD"))
            .locations("classpath:db/migration").sqlMigrationSeparator("-")
            .placeholders(Map.of("demoDatabase",schema,"demoRuntimeUser",runtimeUser)).load().migrate();
    }
    Connection runtime() throws SQLException { return DriverManager.getConnection(url,required("TEST_DB_USER"),required("TEST_DB_PASSWORD")); }
    @Test void migrationsAndMultipleNullMappingsWork() throws Exception {
        try(var c=runtime();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM usermapping WHERE quickbi_user_id IS NULL")) {
            assertTrue(r.next());assertEquals(3,r.getInt(1));
        }
    }
    @Test void uniqueBiMappingCannotBeStolen() throws Exception {
        try(var c=runtime();var s=c.createStatement()) {
            assertThrows(SQLException.class,()->s.executeUpdate("UPDATE usermapping SET quickbi_user_id='bi-alice' WHERE user_id='u-unmapped'"));
        }
    }
    @Test void identityComparisonsAreCaseSensitive() throws Exception {
        try(var c=runtime();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM portaluser WHERE normalized_isid='test001'")) {
            assertTrue(r.next());assertEquals(0,r.getInt(1));
        }
    }
    @Test void runtimeCannotModifyAudit() throws Exception {
        try(var c=runtime();var s=c.createStatement()) {
            assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM auditevent"));
            assertThrows(SQLException.class,()->s.executeUpdate("UPDATE auditevent SET result='forged'"));
        }
    }
    @Test void queuedAndRunningFullCannotCoexist() throws Exception {
        try(var c=runtime();var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO syncjob(id,tenant_id,job_type,triggered_by,state,created_at) VALUES('it-full','demo-tenant','FULL','u-admin','QUEUED',UTC_TIMESTAMP(6))");
            try {
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO syncjob(id,tenant_id,job_type,triggered_by,state,created_at) VALUES('it-full-2','demo-tenant','FULL','u-admin','RUNNING',UTC_TIMESTAMP(6))"));
            } finally { s.executeUpdate("DELETE FROM syncjob WHERE id='it-full'"); }
        }
    }
}

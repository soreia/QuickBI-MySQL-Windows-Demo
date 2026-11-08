package demo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import java.util.*;
import java.util.function.Supplier;
@Component
public class Store {
    public static final String TENANT="demo-tenant";
    public final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public Store(JdbcTemplate jdbc,TransactionTemplate transactions) { this.jdbc=jdbc; this.transactions=transactions; }
    public List<Map<String,Object>> list(String sql,Object...args) { var rows=jdbc.queryForList(sql,args);
        for(var row:rows) row.replaceAll((key,value)-> value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toString() : value instanceof java.time.LocalDateTime date ? date.toInstant(java.time.ZoneOffset.UTC).toString() : value);
        return rows; }
    public Map<String,Object> one(String sql,Object...args) {
        List<Map<String,Object>> rows=list(sql,args); return rows.isEmpty()?null:rows.getFirst();
    }
    public int update(String sql,Object...args) { return jdbc.update(sql,args); }
    public long count(String sql,Object...args) { Long n=jdbc.queryForObject(sql,Long.class,args); return n==null?0:n; }
    public <T> T tx(Supplier<T> action) { return transactions.execute(status->action.get()); }
    public static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    public static String text(Map<String,Object> row,String key) { Object v=row.get(key); return v==null?null:v.toString(); }
    public static String id() { return UUID.randomUUID().toString(); }
}

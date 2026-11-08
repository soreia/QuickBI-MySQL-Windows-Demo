package demo;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class MockBi {
    public record Ticket(String raw,String requestId,Instant expiresAt) {}
    private record Payload(String user,String report,Instant expiresAt) {}
    private final Store db;
    private final Map<String,Payload> tickets=new ConcurrentHashMap<>();
    public volatile String mode="NORMAL";
    public MockBi(Store db) { this.db=db; }
    private void available() { if("READ_UNAVAILABLE".equals(mode)) throw ApiError.unavailable(); }
    public void user(String id) {
        available();
        if(id==null || db.count("SELECT COUNT(*) FROM mockbiuser WHERE id=? AND active=1",id)!=1)
            throw new ApiError(403,"BI_IDENTITY_INVALID","模拟 Quick BI 用户不存在或已停用");
    }
    public List<Map<String,Object>> reports(String id) {
        user(id);
        return db.list("SELECT r.* FROM mockgrant g JOIN reportresource r ON r.id=g.report_id WHERE g.bi_user_id=? AND r.tenant_id=? AND r.published=1 AND r.embed_enabled=1 AND r.work_type IN ('DASHBOARD','WORKBOOK') ORDER BY r.name,r.works_id,r.workspace_id,r.id",id,Store.TENANT);
    }
    public void authorize(String id,String report) {
        user(id);
        if(reports(id).stream().noneMatch(r->report.equals(r.get("id")))) throw new ApiError(404,"REPORT_NOT_ACCESSIBLE","无法访问该报表");
    }
    public Ticket issue(String id,String report) {
        authorize(id,report); // mock vendor checks again; no owner fallback
        if(tickets.size()>10000) throw ApiError.unavailable();
        String raw=Crypto.random(); var expires=Instant.now().plusSeconds(300);
        tickets.put(Crypto.hmac("local-mock-ticket-key",raw),new Payload(id,report,expires));
        if("TICKET_UNKNOWN".equals(mode)) throw new ApiError(503,"TICKET_ISSUE_UNKNOWN","模拟厂商已创建但响应丢失：禁止自动重试");
        return new Ticket(raw,Store.id(),expires);
    }
    public String consume(String raw) {
        if(raw==null || raw.length()>100) throw new ApiError(410,"TICKET_EXPIRED","模拟票据不存在或已使用");
        Payload value=tickets.remove(Crypto.hmac("local-mock-ticket-key",raw));
        if(value==null || !Instant.now().isBefore(value.expiresAt())) throw new ApiError(410,"TICKET_EXPIRED","模拟票据过期或已使用");
        var report=db.one("SELECT name FROM reportresource WHERE tenant_id=? AND id=?",Store.TENANT,value.report());
        var user=db.one("SELECT data_region FROM mockbiuser WHERE id=?",value.user());
        String title=Crypto.html(Store.text(report,"name")),region=Crypto.html(Store.text(user,"data_region"));
        return "<!doctype html><html lang='zh-CN'><meta charset='UTF-8'><meta name='referrer' content='no-referrer'><title>Mock BI</title><style>body{font:16px system-ui;background:#edf4f9;color:#18324b;padding:35px}.grid{display:flex;gap:20px}.card{background:white;padding:24px;border-radius:16px;min-width:130px}strong{font-size:30px;color:#066c72}small{color:#547086}</style><small>LOCAL MOCK QUICK BI · 合成数据，不是真实报表</small><h1>"+title+"</h1><p>厂商侧票据绑定用户："+Crypto.html(value.user())+" · 数据隔离区域："+region+"</p><div class='grid'><div class='card'>模拟销售额<p><strong>"+("bi-alice".equals(value.user())?"¥ 1,280,000":"¥ 860,000")+"</strong></p></div><div class='card'>数据区域<p><strong>"+region+"</strong></p></div></div><p>单次票据已消费；再次使用同一URL将失败。此页面不会回传 VIEW_CONFIRMED。</p></html>";
    }
    @Scheduled(fixedDelay=60000)
    void clean() { tickets.entrySet().removeIf(e->!Instant.now().isBefore(e.getValue().expiresAt())); }
}

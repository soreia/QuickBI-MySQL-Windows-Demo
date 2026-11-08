package demo;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DuplicateKeyException;
import java.time.*;
import java.util.*;
@RestController
public class Api {
    private final Store db; private final Auth auth; private final Portal portal; private final MockBi bi; private final Audit audit; private final Jobs jobs;
    public Api(Store db,Auth auth,Portal portal,MockBi bi,Audit audit,Jobs jobs) { this.db=db; this.auth=auth; this.portal=portal; this.bi=bi; this.audit=audit; this.jobs=jobs; }
    private Auth.Principal principal(HttpServletRequest r) { return (Auth.Principal)r.getAttribute("principal"); }
    private Map<String,Object> ok(HttpServletRequest r,Object data) {
        var p=principal(r); String path=r.getRequestURI();
        if(p!=null && !path.equals("/api/v1/me") && !(r.getMethod().equals("GET") && path.startsWith("/api/v1/admin/sync-jobs"))) auth.activity(p);
        return Map.of("requestId",r.getAttribute("requestId"),"data",data);
    }
    public record Login(String userId) {}
    public record Mapping(String quickbiUserId,long expectedVersion,String reason) {}
    public record Sync(String type,String userId) {}
    public record Config(long expectedVersion,int idleMinutes,int absoluteHours,int listCacheSeconds,String reason) {}
    public record Fault(String mode) {}
    public record Grant(String biUserId,String reportId,boolean granted) {}
    @GetMapping("/healthz/ready") public Map<String,Object> health() { db.count("SELECT COUNT(*) FROM systemconfig"); return Map.of("status","UP","mode","LOCAL-MOCK"); }
    @GetMapping("/auth/context") public Object context(HttpServletRequest r,HttpServletResponse s) { return ok(r,auth.context(s)); }
    @PostMapping("/auth/login") public Object login(@RequestBody Login body,HttpServletRequest r,HttpServletResponse s) {
        if(body.userId()==null) throw new ApiError(422,"VALIDATION_FAILED","请选择演示账号"); return ok(r,auth.login(r,s,body.userId()));
    }
    @GetMapping("/api/v1/me") public Object me(HttpServletRequest r) { return ok(r,auth.me(principal(r))); }
    @PostMapping("/api/v1/auth/logout") public ResponseEntity<Void> logout(HttpServletRequest r,HttpServletResponse s) {
        if(principal(r)!=null) auth.logout(principal(r)); auth.setCookie(s,"PORTAL-SID","",0); return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/v1/reports") public Object reports(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest r) {
        return ok(r,portal.reports(principal(r),q,page,pageSize));
    }
    @PostMapping("/api/v1/reports/{id}/embed-sessions") public Object open(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody Map<String,Object> body,HttpServletRequest r) {
        if(!id.matches("[A-Za-z0-9.-]{1,64}")) throw new ApiError(400,"INVALID_REPORT_ID","报表标识格式错误");
        if(!body.isEmpty()) throw new ApiError(400,"UNEXPECTED_FIELDS","签票接口不接受身份、角色、URL或其他参数");
        return ok(r,portal.open(principal(r),id,key));
    }
    @GetMapping(value="/mock/bi/view",produces=MediaType.TEXT_HTML_VALUE) public String view(@RequestParam String ticket) { return bi.consume(ticket); }
    @PostMapping("/api/v1/embed-events") public Object embedEvent(@RequestBody Map<String,String> body,HttpServletRequest r) {
        if(!body.keySet().equals(Set.of("embedSessionId"))) throw new ApiError(400,"UNEXPECTED_FIELDS","仅接受embedSessionId");
        var p=principal(r); var a=db.one("SELECT id FROM accessattempt WHERE tenant_id=? AND id=? AND user_id=? AND state='CREATED'",Store.TENANT,body.get("embedSessionId"),p.userId());
        if(a==null) throw new ApiError(404,"ATTEMPT_NOT_FOUND","打开尝试不存在");
        audit.write(p.userId(),"EMBED_NAVIGATED","CLIENT_SIGNAL",body.get("embedSessionId"),p.requestId(),"iframe onload only; NOT REPORT_VIEW_CONFIRMED");
        return ok(r,Map.of("accepted",true));
    }
    @GetMapping("/api/v1/admin/users") public Object users(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest r) {
        auth.role(principal(r),"ADMIN"); Portal.query(q); Portal.page(page); Portal.size(pageSize);
        String from=" FROM portaluser u JOIN usermapping m ON m.user_id=u.id WHERE u.tenant_id=? AND (u.normalized_isid LIKE ? ESCAPE '!' OR u.display_name COLLATE utf8mb4_0900_as_ci LIKE ? ESCAPE '!')";
        String pattern=Portal.like(q);
        return ok(r,Map.of("items",db.list("SELECT u.id userId,u.normalized_isid isid,u.display_name displayName,u.roles,u.enterprise_status enterpriseStatus,m.quickbi_user_id quickbiUserId,m.mapping_status mappingStatus,m.version version"+from+" ORDER BY u.id LIMIT ? OFFSET ?",Store.TENANT,pattern,pattern,pageSize,(page-1)*pageSize),"total",db.count("SELECT COUNT(*)"+from,Store.TENANT,pattern,pattern),"page",page,"pageSize",pageSize));
    }
    @PutMapping("/api/v1/admin/users/{id}/mapping") public Object mapping(@PathVariable String id,@RequestBody Mapping body,HttpServletRequest r) {
        if(body.quickbiUserId()==null || body.quickbiUserId().isBlank()) throw new ApiError(422,"VALIDATION_FAILED","需要目标BI用户ID");
        portal.mapping(principal(r),id,body.quickbiUserId(),body.expectedVersion(),body.reason());
        return ok(r,Map.of("version",body.expectedVersion()+1,"mappingStatus","MAPPED"));
    }
    @PostMapping("/api/v1/admin/sync-jobs") public ResponseEntity<?> createJob(@RequestBody Sync body,HttpServletRequest r) {
        auth.role(principal(r),"ADMIN"); if(body.type()==null) throw new ApiError(422,"VALIDATION_FAILED","需要type");
        if(!Set.of("FULL","USER").contains(body.type())) throw new ApiError(422,"VALIDATION_FAILED","type仅支持FULL或USER；重试请调用retry接口");
        return ResponseEntity.accepted().body(ok(r,jobs.create(principal(r),body.type(),body.userId(),null)));
    }
    @GetMapping("/api/v1/admin/sync-jobs") public Object jobs(HttpServletRequest r) {
        auth.role(principal(r),"ADMIN"); return ok(r,Map.of("items",db.list("SELECT * FROM syncjob WHERE tenant_id=? ORDER BY created_at DESC LIMIT 100",Store.TENANT),"limit",100));
    }
    @GetMapping("/api/v1/admin/sync-jobs/{id}") public Object job(@PathVariable String id,HttpServletRequest r) {
        auth.role(principal(r),"ADMIN"); var job=db.one("SELECT * FROM syncjob WHERE tenant_id=? AND id=?",Store.TENANT,id);
        if(job==null) throw new ApiError(404,"TASK_NOT_FOUND","任务不存在"); return ok(r,job);
    }
    @GetMapping("/api/v1/admin/sync-jobs/{id}/items") public Object items(@PathVariable String id,HttpServletRequest r) {
        job(id,r); return ok(r,Map.of("items",db.list("SELECT * FROM syncitem WHERE job_id=? ORDER BY user_id LIMIT 100",id),"limit",100));
    }
    @PostMapping("/api/v1/admin/sync-jobs/{id}/retry") public ResponseEntity<?> retry(@PathVariable String id,HttpServletRequest r) {
        auth.role(principal(r),"ADMIN"); return ResponseEntity.accepted().body(ok(r,jobs.create(principal(r),"RETRY_FAILED",null,id)));
    }
    @GetMapping("/api/v1/admin/audit-events") public Object events(@RequestParam(required=false) String from,@RequestParam(required=false) String to,
        @RequestParam(defaultValue="") String eventType,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest r) {
        auth.role(principal(r),"ADMIN","AUDITOR"); Portal.page(page); Portal.size(pageSize); Portal.query(q);
        Instant end=to==null?Instant.now():Instant.parse(to),start=from==null?end.minus(Duration.ofDays(7)):Instant.parse(from);
        if(!start.isBefore(end) || Duration.between(start,end).compareTo(Duration.ofDays(31))>0) throw new ApiError(422,"INVALID_TIME_RANGE","审计查询范围须大于0且不超过31天");
        String clause=" FROM auditevent WHERE tenant_id=? AND event_time>=? AND event_time<? AND (?='' OR event_type=?) AND (?='' OR result=?) AND (safe_details LIKE ? ESCAPE '!' OR actor_id LIKE ? ESCAPE '!')";
        Object[] args={Store.TENANT,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),eventType,eventType,status,status,Portal.like(q),Portal.like(q)};
        List<Object> paged=new ArrayList<>(Arrays.asList(args)); paged.add(pageSize); paged.add((page-1)*pageSize);
        return ok(r,Map.of("items",db.list("SELECT *"+clause+" ORDER BY event_time DESC,id LIMIT ? OFFSET ?",paged.toArray()),"total",db.count("SELECT COUNT(*)"+clause,args),"page",page,"pageSize",pageSize));
    }
    @GetMapping("/api/v1/admin/config") public Object config(HttpServletRequest r) { auth.role(principal(r),"ADMIN"); return ok(r,db.one("SELECT version,idle_minutes idleMinutes,absolute_hours absoluteHours,cache_seconds listCacheSeconds FROM systemconfig WHERE tenant_id=?",Store.TENANT)); }
    @PatchMapping("/api/v1/admin/config") public Object configSave(@RequestBody Config body,HttpServletRequest r) {
        var p=principal(r); auth.role(p,"ADMIN"); Portal.reason(body.reason());
        if(body.idleMinutes()<5 || body.idleMinutes()>60 || body.absoluteHours()<1 || body.absoluteHours()>12 || body.listCacheSeconds()<0 || body.listCacheSeconds()>300) throw new ApiError(422,"VALIDATION_FAILED","会话/缓存参数超出允许范围");
        db.tx(()->{
            db.one("SELECT version FROM systemconfig WHERE tenant_id=? FOR UPDATE",Store.TENANT);
            db.update("UPDATE portalsession s JOIN systemconfig c ON c.tenant_id=s.tenant_id SET s.revoked_at=UTC_TIMESTAMP(6) WHERE s.tenant_id=? AND s.revoked_at IS NULL AND (UTC_TIMESTAMP(6)>=TIMESTAMPADD(MINUTE,c.idle_minutes,s.last_activity) OR UTC_TIMESTAMP(6)>=TIMESTAMPADD(HOUR,c.absolute_hours,s.created_at))",Store.TENANT);
            if(db.update("UPDATE systemconfig SET idle_minutes=?,absolute_hours=?,cache_seconds=?,version=version+1 WHERE tenant_id=? AND version=?",body.idleMinutes(),body.absoluteHours(),body.listCacheSeconds(),Store.TENANT,body.expectedVersion())!=1) throw new ApiError(409,"VERSION_CONFLICT","配置版本冲突");
            audit.write(p.userId(),"ADMIN_CHANGE","SUCCESS","config",p.requestId(),"idle="+body.idleMinutes()+"; absolute="+body.absoluteHours()+"; cache="+body.listCacheSeconds()+"; reason="+body.reason()); return null;
        });
        return config(r);
    }
    // Explicitly LOCAL MOCK provider control panel. Not a production portal authorization API.
    @GetMapping("/api/v1/demo/state") public Object mockState(HttpServletRequest r) {
        auth.role(principal(r),"ADMIN"); return ok(r,Map.of("mode",bi.mode,"auditFailure",audit.simulateFailure,"biUsers",db.list("SELECT * FROM mockbiuser"),"grants",db.list("SELECT * FROM mockgrant"),"reports",db.list("SELECT id,name,published FROM reportresource WHERE tenant_id=?",Store.TENANT)));
    }
    @PostMapping("/api/v1/demo/fault") public Object fault(@RequestBody Fault body,HttpServletRequest r) {
        var p=principal(r); auth.role(p,"ADMIN");
        if(body.mode()==null || !Set.of("NORMAL","READ_UNAVAILABLE","TICKET_UNKNOWN","AUDIT_FAILURE").contains(body.mode())) throw new ApiError(422,"VALIDATION_FAILED","Mock故障模式不合法");
        // Recovery must remain possible while simulated audit is unavailable.
        audit.simulateFailure=false;
        audit.write(p.userId(),"MOCK_CONTROL","CHANGED",null,p.requestId(),"mode="+body.mode());
        bi.mode="AUDIT_FAILURE".equals(body.mode())?"NORMAL":body.mode(); audit.simulateFailure="AUDIT_FAILURE".equals(body.mode());
        return ok(r,Map.of("mode",body.mode()));
    }
    @PostMapping("/api/v1/demo/grant") public Object mockGrant(@RequestBody Grant body,HttpServletRequest r) {
        var p=principal(r); auth.role(p,"ADMIN"); bi.user(body.biUserId());
        if(db.count("SELECT COUNT(*) FROM reportresource WHERE tenant_id=? AND id=?",Store.TENANT,body.reportId())!=1) throw new ApiError(422,"VALIDATION_FAILED","Mock资源不存在");
        db.tx(()->{
            if(body.granted()) db.update("INSERT INTO mockgrant VALUES (?,?) ON DUPLICATE KEY UPDATE report_id=report_id",body.biUserId(),body.reportId());
            else db.update("DELETE FROM mockgrant WHERE bi_user_id=? AND report_id=?",body.biUserId(),body.reportId());
            audit.write(p.userId(),"MOCK_CONTROL","CHANGED",body.reportId(),p.requestId(),"MOCK authority grant="+body.granted()+" for "+body.biUserId()); return null;
        }); return ok(r,Map.of("changed",true));
    }
}

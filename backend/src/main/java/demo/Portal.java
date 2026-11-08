package demo;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
@Service
public class Portal {
    private final Store db; private final Auth auth; private final MockBi bi; private final Audit audit;
    public Portal(Store db,Auth auth,MockBi bi,Audit audit) { this.db=db; this.auth=auth; this.bi=bi; this.audit=audit; }
    private record Lease(String owner,long fence,long version,String biUser) {}
    public void refresh(String userId) { refresh(userId,null,null,0); }
    public void refresh(String userId,String jobId,String jobOwner,long jobFence) {
        Lease lease=db.tx(()->{
            if(db.one("SELECT id FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,userId)==null) throw new ApiError(404,"USER_NOT_FOUND","用户不存在");
            var m=db.one("SELECT * FROM usermapping WHERE tenant_id=? AND user_id=? FOR UPDATE",Store.TENANT,userId);
            if(!"MAPPED".equals(m.get("mapping_status"))) throw new ApiError(403,"BI_MAPPING_REQUIRED","用户没有有效映射");
            String owner=Store.id();
            if(db.update("UPDATE usercoord SET lease_owner=?,lease_until=TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)),fence=fence+1 WHERE tenant_id=? AND user_id=? AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6))",owner,Store.TENANT,userId)!=1)
                throw new ApiError(409,"REFRESH_BUSY","该用户正在刷新，请稍后重试");
            var c=db.one("SELECT fence FROM usercoord WHERE tenant_id=? AND user_id=?",Store.TENANT,userId);
            return new Lease(owner,Store.number(c,"fence"),Store.number(m,"version"),Store.text(m,"quickbi_user_id"));
        });
        try {
            var reports=bi.reports(lease.biUser()); // outside local transaction
            db.tx(()->{
                db.one("SELECT id FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,userId);
                var m=db.one("SELECT version FROM usermapping WHERE tenant_id=? AND user_id=? FOR UPDATE",Store.TENANT,userId);
                if(Store.number(m,"version")!=lease.version()) throw new ApiError(409,"VERSION_CONFLICT","映射变化，放弃旧刷新结果");
                var c=db.one("SELECT fence FROM usercoord WHERE tenant_id=? AND user_id=? AND lease_owner=? AND fence=? AND lease_until>UTC_TIMESTAMP(6) FOR UPDATE",Store.TENANT,userId,lease.owner(),lease.fence());
                if(c==null) throw new ApiError(409,"LEASE_LOST","刷新租约已失效");
                if(jobId!=null && db.one("SELECT id FROM syncjob WHERE id=? AND tenant_id=? AND state='RUNNING' AND lease_owner=? AND fence=? AND lease_until>UTC_TIMESTAMP(6) FOR UPDATE",jobId,Store.TENANT,jobOwner,jobFence)==null)
                    throw new ApiError(409,"LEASE_LOST","任务租约已失效，不发布旧结果");
                String snapshot=Store.id();
                db.update("INSERT INTO snapshot VALUES (?,?,?,?,?,UTC_TIMESTAMP(6),1)",snapshot,Store.TENANT,userId,lease.version(),lease.fence());
                for(var r:reports) db.update("INSERT INTO snapshotitem VALUES (?,?,?)",Store.TENANT,snapshot,r.get("id"));
                db.update("UPDATE usercoord SET current_snapshot=?,lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND user_id=? AND fence=?",snapshot,Store.TENANT,userId,lease.fence());
                return null;
            });
        } finally {
            db.update("UPDATE usercoord SET lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND user_id=? AND lease_owner=? AND fence=?",Store.TENANT,userId,lease.owner(),lease.fence());
        }
    }
    public static int page(int p) { if(p<1 || p>100000) throw new ApiError(400,"INVALID_PAGE","page需在1到100000之间"); return p; }
    public static int size(int s) { if(s<1 || s>100) throw new ApiError(400,"INVALID_PAGE_SIZE","pageSize需在1到100之间"); return s; }
    public static String query(String q) {
        if(q==null) return "";
        if(q.length()>100) throw new ApiError(400,"INVALID_QUERY","搜索最多100字符"); return q;
    }
    public static String like(String q) { return "%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
    private Map<String,Object> snapshot(String user) {
        return db.one("SELECT s.*,TIMESTAMPADD(SECOND,c.cache_seconds,s.fetched_at) valid_until FROM usercoord u JOIN snapshot s ON s.id=u.current_snapshot AND s.tenant_id=u.tenant_id JOIN usermapping m ON m.user_id=u.user_id AND m.tenant_id=u.tenant_id JOIN systemconfig c ON c.tenant_id=u.tenant_id WHERE u.tenant_id=? AND u.user_id=? AND s.mapping_version=m.version AND s.complete=1 AND UTC_TIMESTAMP(6)<TIMESTAMPADD(SECOND,c.cache_seconds,s.fetched_at)",Store.TENANT,user);
    }
    public Map<String,Object> reports(Auth.Principal p,String q,int page,int pageSize) {
        auth.role(p,"USER","ADMIN","AUDITOR"); auth.mapping(p); query(q); page(page); size(pageSize);
        var snap=snapshot(p.userId());
        if(snap==null) { refresh(p.userId()); snap=snapshot(p.userId()); }
        // TTL=0 intentionally forces refresh each time, then uses just-published result for this response.
        if(snap==null) snap=db.one("SELECT s.*,s.fetched_at valid_until FROM usercoord c JOIN snapshot s ON s.id=c.current_snapshot AND s.tenant_id=c.tenant_id JOIN usermapping m ON m.user_id=c.user_id AND m.tenant_id=c.tenant_id WHERE c.tenant_id=? AND c.user_id=? AND s.mapping_version=m.version AND m.mapping_status='MAPPED' AND s.complete=1",Store.TENANT,p.userId());
        if(snap==null) throw ApiError.unavailable();
        String filter=" FROM snapshotitem i JOIN reportresource r ON r.id=i.report_id AND r.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.snapshot_id=? AND (r.name COLLATE utf8mb4_0900_as_ci LIKE ? ESCAPE '!' OR r.description COLLATE utf8mb4_0900_as_ci LIKE ? ESCAPE '!')";
        var args=new Object[]{Store.TENANT,snap.get("id"),like(q),like(q)};
        long total=db.count("SELECT COUNT(*)"+filter,args);
        List<Object> paged=new ArrayList<>(Arrays.asList(args)); paged.add(pageSize); paged.add((page-1)*pageSize);
        var rows=db.list("SELECT r.id reportId,r.name,r.description,r.work_type type,'PUBLISHED' publishStatus"+filter+" ORDER BY r.name,r.works_id,r.workspace_id,r.id LIMIT ? OFFSET ?",paged.toArray());
        auth.activity(p);
        return Map.of("items",rows,"total",total,"page",page,"pageSize",pageSize,"snapshotAt",snap.get("fetched_at"),"validUntil",snap.get("valid_until"));
    }
    public Map<String,Object> open(Auth.Principal p,String report,String idempotencyKey) {
        auth.role(p,"USER","ADMIN","AUDITOR");
        if(idempotencyKey==null || !idempotencyKey.matches("[A-Za-z0-9.-]{8,128}")) throw new ApiError(400,"INVALID_IDEMPOTENCY_KEY","Idempotency-Key需为8～128位字母数字、点或连字符");
        var m=auth.mapping(p); long version=Store.number(m,"version");
        String attempt=Store.id(),keyHash=auth.keyHash(idempotencyKey);
        db.tx(()->{
            db.one("SELECT id FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,p.userId());
            try { db.update("INSERT INTO requestguard VALUES (?,?,?,?,?,?,'PROCESSING',TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)))",Store.TENANT,p.userId(),p.sidHash(),report,keyHash,attempt); }
            catch(DuplicateKeyException e) {
                if(db.update("UPDATE requestguard SET attempt_id=?,state='PROCESSING',expires_at=TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)) WHERE tenant_id=? AND user_id=? AND sid_hash=? AND report_id=? AND key_hash=? AND expires_at<=UTC_TIMESTAMP(6) AND state<>'PROCESSING'",attempt,Store.TENANT,p.userId(),p.sidHash(),report,keyHash)!=1)
                    throw new ApiError(409,"DUPLICATE_OPEN_REQUEST","重复打开请求不重放票据，请显式重试使用新键");
            }
            if(db.count("SELECT COUNT(*) FROM accessattempt WHERE tenant_id=? AND user_id=? AND created_at>TIMESTAMPADD(MINUTE,-1,UTC_TIMESTAMP(6))",Store.TENANT,p.userId())>=10)
                throw new ApiError(429,"RATE_LIMITED","每用户每分钟最多10次打开尝试");
            db.update("INSERT INTO accessattempt VALUES (?,?,?,?,?,?,'PROCESSING',?,NULL,NULL,NULL,UTC_TIMESTAMP(6))",attempt,Store.TENANT,p.userId(),report,version,p.requestId());
            return null;
        });
        try {
            String user=Store.text(m,"quickbi_user_id"); bi.authorize(user,report);
            audit.write(p.userId(),"ACCESS_DECISION","ALLOW",report,p.requestId(),"Online mock authority; mappingVersion="+version);
            MockBi.Ticket ticket=bi.issue(user,report);
            db.tx(()->{
                db.one("SELECT id FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,p.userId());
                var current=db.one("SELECT version FROM usermapping WHERE tenant_id=? AND user_id=? FOR UPDATE",Store.TENANT,p.userId());
                try { auth.valid(p.sidHash(),true); }
                catch(ApiError e) { throw new ApiError(401,"AUTH_REQUIRED","签票后会话已失效，放弃交付"); }
                if(Store.number(current,"version")!=version) throw new ApiError(409,"VERSION_CONFLICT","签票期间映射变化，放弃交付");
                db.update("UPDATE accessattempt SET state='CREATED',provider_request_id=?,issued_at=UTC_TIMESTAMP(6),expires_at=? WHERE id=?",ticket.requestId(),java.sql.Timestamp.from(ticket.expiresAt()),attempt);
                audit.write(p.userId(),"TICKET_ISSUE","CREATED",report,p.requestId(),"Provider request="+ticket.requestId()+"; single-use; expires="+ticket.expiresAt());
                db.update("UPDATE requestguard SET state='CREATED' WHERE attempt_id=?",attempt);
                return null;
            });
            auth.activity(p);
            return Map.of("embedSessionId",attempt,"embedUrl","/mock/bi/view?ticket="+ticket.raw(),"expiresAt",ticket.expiresAt().toString(),"ticketUseLimit",1);
        } catch(Exception error) {
            String state=error instanceof ApiError a && "TICKET_ISSUE_UNKNOWN".equals(a.code)?"ISSUE_UNKNOWN":
                error instanceof ApiError a && (a.status==401 || "VERSION_CONFLICT".equals(a.code))?"ABANDONED":"FAILED";
            // Never retry the vendor operation. If this write fails, leave PROCESSING for recovery diagnosis.
            try { db.tx(()->{
                db.update("UPDATE accessattempt SET state=? WHERE id=?",state,attempt);
                db.update("UPDATE requestguard SET state=? WHERE attempt_id=?",state,attempt);
                if(error instanceof ApiError a && a.status==404) audit.write(p.userId(),"ACCESS_DECISION","DENY",report,p.requestId(),"REPORT_NOT_ACCESSIBLE");
                audit.write(p.userId(),"TICKET_ISSUE",state,report,p.requestId(),error instanceof ApiError a?a.code:"LOCAL_PERSISTENCE_FAILURE"); return null;
            }); } catch(Exception ignored) { /* fail closed; no raw URL or ticket logging */ }
            throw error;
        }
    }
    public void mapping(Auth.Principal actor,String user,String biUser,long expected,String reason) {
        auth.role(actor,"ADMIN"); reason(reason);
        if(biUser==null || biUser.isBlank() || biUser.length()>256) throw new ApiError(422,"VALIDATION_FAILED","BI用户ID格式错误");
        try { bi.user(biUser); }
        catch(ApiError e) { if(e.status==403) throw new ApiError(422,"BI_TARGET_INVALID","目标BI用户不存在或不可用"); else throw e; }
        db.tx(()->{
            if(db.one("SELECT id FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,user)==null) throw new ApiError(404,"USER_NOT_FOUND","用户不存在");
            var m=db.one("SELECT * FROM usermapping WHERE tenant_id=? AND user_id=? FOR UPDATE",Store.TENANT,user);
            if(Store.number(m,"version")!=expected) throw new ApiError(409,"VERSION_CONFLICT","映射版本已变化");
            db.update("UPDATE usermapping SET quickbi_user_id=?,mapping_status='MAPPED',bi_status='ACTIVE',version=version+1,change_reason=? WHERE tenant_id=? AND user_id=?",biUser,reason,Store.TENANT,user);
            db.update("UPDATE portaluser SET security_epoch=security_epoch+1 WHERE tenant_id=? AND id=?",Store.TENANT,user);
            db.update("UPDATE portalsession SET revoked_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND user_id=?",Store.TENANT,user);
            db.update("UPDATE usercoord SET current_snapshot=NULL,fence=fence+1,lease_owner=NULL,lease_until=NULL WHERE tenant_id=? AND user_id=?",Store.TENANT,user);
            audit.write(actor.userId(),"ADMIN_CHANGE","SUCCESS",user,actor.requestId(),"mapping "+Store.text(m,"quickbi_user_id")+" -> "+biUser+"; reason="+reason); return null;
        });
    }
    public static void reason(String reason) { if(reason==null || reason.isBlank() || reason.length()>500) throw new ApiError(422,"VALIDATION_FAILED","reason需为1～500字符"); }
}

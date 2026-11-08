package demo;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
@Service
public class Jobs {
    private final Store db; private final Portal portal; private final Audit audit;
    public Jobs(Store db,Portal portal,Audit audit) { this.db=db; this.portal=portal; this.audit=audit; }
    public Map<String,Object> create(Auth.Principal p,String type,String user,String parent) {
        if(!Set.of("FULL","USER","RETRY_FAILED").contains(type)) throw new ApiError(422,"VALIDATION_FAILED","任务类型不合法");
        try { return db.tx(()->{
            if("FULL".equals(type)) {
                var existing=db.one("SELECT id FROM syncjob WHERE tenant_id=? AND active_full_tenant=?",Store.TENANT,Store.TENANT);
                if(existing!=null) return Map.of("taskId",existing.get("id"),"deduplicated",true);
            }
            List<Map<String,Object>> users;
            if("RETRY_FAILED".equals(type)) {
                var prior=db.one("SELECT state FROM syncjob WHERE id=? AND tenant_id=?",parent,Store.TENANT);
                if(prior==null) throw new ApiError(404,"TASK_NOT_FOUND","父任务不存在");
                if(Set.of("RUNNING","QUEUED").contains(prior.get("state"))) throw new ApiError(409,"TASK_NOT_TERMINAL","父任务尚未终止");
                users=db.list("SELECT user_id id FROM syncitem WHERE job_id=? AND state<>'SUCCEEDED'",parent);
                if(users.isEmpty()) throw new ApiError(422,"NO_RETRY_ITEMS","没有失败或跳过项");
            } else if("USER".equals(type)) {
                users=db.list("SELECT id FROM portaluser WHERE tenant_id=? AND id=?",Store.TENANT,user);
                if(users.isEmpty()) throw new ApiError(404,"USER_NOT_FOUND","用户不存在");
            } else users=db.list("SELECT user_id id FROM usermapping WHERE tenant_id=? AND quickbi_user_id IS NOT NULL ORDER BY user_id",Store.TENANT);
            String id=Store.id();
            db.update("INSERT INTO syncjob(id,tenant_id,job_type,parent_id,triggered_by,state,total,created_at) VALUES (?,?,?,?,?,'QUEUED',?,UTC_TIMESTAMP(6))",id,Store.TENANT,type,parent,p.userId(),users.size());
            for(var u:users) db.update("INSERT INTO syncitem VALUES (?,?,'QUEUED',NULL,0)",id,u.get("id"));
            audit.write(p.userId(),"SYNC","QUEUED",id,p.requestId(),"type="+type+"; targets="+users.size());
            return Map.of("taskId",id,"deduplicated",false,"state","QUEUED");
        }); } catch(DuplicateKeyException e) {
            if("FULL".equals(type)) {
                var existing=db.one("SELECT id FROM syncjob WHERE tenant_id=? AND active_full_tenant=?",Store.TENANT,Store.TENANT);
                if(existing!=null) return Map.of("taskId",existing.get("id"),"deduplicated",true);
            }
            throw new ApiError(409,"TASK_CONFLICT","任务状态变化，请重试查询");
        }
    }
    @Scheduled(fixedDelay=1500)
    public void tick() {
        try { execute(); } catch(Exception ignored) { /* durable job/lease remains; next tick diagnoses interruption */ }
    }
    private void execute() {
        String owner=Store.id();
        var job=db.tx(()->{
            var expired=db.list("SELECT id FROM syncjob WHERE tenant_id=? AND state='RUNNING' AND lease_until<UTC_TIMESTAMP(6) FOR UPDATE",Store.TENANT);
            for(var old:expired) {
                db.update("UPDATE syncitem SET state='INTERRUPTED',error_code='LEASE_EXPIRED' WHERE job_id=? AND state IN ('QUEUED','RUNNING')",old.get("id"));
                db.update("UPDATE syncjob SET state='INTERRUPTED',ended_at=UTC_TIMESTAMP(6),fence=fence+1 WHERE id=?",old.get("id"));
                recount(Store.text(old,"id"));
            }
            var next=db.one("SELECT * FROM syncjob WHERE tenant_id=? AND state='QUEUED' ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",Store.TENANT);
            if(next==null) return null;
            db.update("UPDATE syncjob SET state='RUNNING',lease_owner=?,lease_until=TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)),fence=fence+1 WHERE id=?",owner,next.get("id"));
            return db.one("SELECT * FROM syncjob WHERE id=?",next.get("id"));
        });
        if(job==null) return;
        String id=Store.text(job,"id"); long fence=Store.number(job,"fence");
        for(var item:db.list("SELECT * FROM syncitem WHERE job_id=? ORDER BY user_id",id)) {
            if(db.update("UPDATE syncjob SET lease_until=TIMESTAMPADD(SECOND,30,UTC_TIMESTAMP(6)) WHERE id=? AND state='RUNNING' AND lease_owner=? AND fence=? AND lease_until>UTC_TIMESTAMP(6)",id,owner,fence)!=1) return;
            String state="SUCCEEDED",error=null;
            try { portal.refresh(Store.text(item,"user_id"),id,owner,fence); }
            catch(ApiError e) { state="REFRESH_BUSY".equals(e.code)?"SKIPPED_BUSY":"FAILED"; error=e.code; }
            catch(Exception e) { state="FAILED"; error="LOCAL_STORAGE_FAILURE"; }
            String finalState=state,finalError=error;
            db.tx(()->{
                var lease=db.one("SELECT id FROM syncjob WHERE id=? AND lease_owner=? AND fence=? AND state='RUNNING' AND lease_until>UTC_TIMESTAMP(6) FOR UPDATE",id,owner,fence);
                if(lease!=null) db.update("UPDATE syncitem SET state=?,error_code=?,attempt_count=attempt_count+1 WHERE job_id=? AND user_id=?",finalState,finalError,id,item.get("user_id"));
                return null;
            });
        }
        db.tx(()->{
            var lease=db.one("SELECT id FROM syncjob WHERE id=? AND lease_owner=? AND fence=? AND state='RUNNING' AND lease_until>UTC_TIMESTAMP(6) FOR UPDATE",id,owner,fence);
            if(lease==null) return null;
            recount(id);
            var n=db.one("SELECT * FROM syncjob WHERE id=?",id);
            String state=Store.number(n,"failed")==0 && Store.number(n,"skipped")==0?"SUCCEEDED":Store.number(n,"success")>0 || Store.number(n,"skipped")>0?"PARTIAL":"FAILED";
            db.update("UPDATE syncjob SET state=?,ended_at=UTC_TIMESTAMP(6),lease_until=NULL WHERE id=?",state,id);
            audit.write(Store.text(job,"triggered_by"),"SYNC",state,id,id,"total="+n.get("total")+"; success="+n.get("success")+"; failed="+n.get("failed")+"; skipped="+n.get("skipped")); return null;
        });
    }
    private void recount(String id) {
        db.update("UPDATE syncjob SET success=(SELECT COUNT(*) FROM syncitem WHERE job_id=? AND state='SUCCEEDED'),skipped=(SELECT COUNT(*) FROM syncitem WHERE job_id=? AND state='SKIPPED_BUSY'),failed=(SELECT COUNT(*) FROM syncitem WHERE job_id=? AND state IN ('FAILED','INTERRUPTED')) WHERE id=?",id,id,id,id);
    }
    @Scheduled(fixedDelay=60000)
    public void cleanup() {
        try {
            db.update("UPDATE requestguard g JOIN accessattempt a ON a.id=g.attempt_id SET g.state='ISSUE_UNKNOWN',a.state='ISSUE_UNKNOWN' WHERE g.state='PROCESSING' AND g.expires_at<TIMESTAMPADD(MINUTE,-5,UTC_TIMESTAMP(6))");
            db.update("DELETE FROM requestguard WHERE expires_at<UTC_TIMESTAMP(6) AND state<>'PROCESSING' LIMIT 1000");
            db.update("DELETE FROM portalsession WHERE revoked_at<TIMESTAMPADD(DAY,-1,UTC_TIMESTAMP(6)) OR created_at<TIMESTAMPADD(DAY,-1,UTC_TIMESTAMP(6)) LIMIT 1000");
        } catch(Exception ignored) { /* no audit deletion, no authorization fallback */ }
    }
}

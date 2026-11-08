package demo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.*;
import java.util.*;
@Component
public class Auth {
    public record Principal(String userId,String sidHash,String sid,long epoch,String requestId) {}
    private final Store db;
    private final Audit audit;
    private final String key,generation;
    private final Set<String> origins;
    public Auth(Store db,Audit audit,@Value("${demo.hmac-key}") String key,@Value("${demo.generation}") String generation,
                @Value("${demo.origins}") String origins,@Value("${demo.mode}") String mode) {
        if(!"mock".equals(mode)) throw new IllegalStateException("This artifact is a LOCAL MOCK DEMO, not a production SSO deployment.");
        if(key.length()<32) throw new IllegalArgumentException("DEMO_HMAC_KEY must have at least 32 characters");
        this.db=db; this.audit=audit; this.key=key; this.generation=generation; this.origins=Set.of(origins.split(","));
    }
    public String hash(String raw) { return Crypto.hmac(key,"sid:"+raw); }
    public String csrf(String raw) { return Crypto.hmac(key,"csrf:"+raw); }
    public String keyHash(String raw) { return Crypto.hmac(key,"guard:"+raw); }
    public static String cookie(HttpServletRequest r,String name) {
        if(r.getCookies()!=null) for(Cookie c:r.getCookies()) if(c.getName().equals(name)) return c.getValue();
        return null;
    }
    public void setCookie(HttpServletResponse res,String name,String value,int age) {
        // HTTP localhost only. Production must use HTTPS and Secure cookies.
        res.addHeader("Set-Cookie",name+"="+value+"; Path=/; HttpOnly; SameSite=Strict; Max-Age="+age);
    }
    public void origin(HttpServletRequest r) {
        if(!origins.contains(r.getHeader("Origin"))) throw new ApiError(403,"ORIGIN_REJECTED","Origin不在本地允许列表");
    }
    public Map<String,Object> context(HttpServletResponse res) {
        String token=Crypto.random(); setCookie(res,"DEMO-LOGIN",token,300);
        return Map.of("mode","LOCAL MOCK — 非企业认证","csrfToken",token,"users",db.list("SELECT id,display_name,roles,normalized_isid FROM portaluser WHERE tenant_id=? ORDER BY id",Store.TENANT));
    }
    public Map<String,Object> login(HttpServletRequest req,HttpServletResponse res,String userId) {
        origin(req);
        if(!Crypto.equal(cookie(req,"DEMO-LOGIN"),req.getHeader("X-CSRF-Token"))) throw new ApiError(403,"CSRF_INVALID","登录CSRF校验失败，请刷新页面");
        String sid=Crypto.random(); String sidHash=hash(sid);
        db.tx(()->{
            var user=db.one("SELECT * FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,userId);
            if(user==null || "INACTIVE".equals(user.get("enterprise_status"))) throw ApiError.forbidden();
            String old=cookie(req,"PORTAL-SID");
            if(old!=null) db.update("UPDATE portalsession SET revoked_at=UTC_TIMESTAMP(6) WHERE sid_hash=?",hash(old));
            db.update("INSERT INTO portalsession VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),NULL)",sidHash,Store.TENANT,userId,Store.number(user,"security_epoch"),generation);
            audit.write(userId,"LOGIN","SUCCESS",null,(String)req.getAttribute("requestId"),"Local mock identity selection, not OIDC");
            return null;
        });
        setCookie(res,"PORTAL-SID",sid,43200); setCookie(res,"DEMO-LOGIN","",0);
        return Map.of("userId",userId,"csrfToken",csrf(sid));
    }
    public Principal authenticate(HttpServletRequest req) {
        String sid=cookie(req,"PORTAL-SID");
        if(sid==null || sid.length()>100) throw new ApiError(401,"AUTH_REQUIRED","请登录本地演示账号");
        String sh=hash(sid);
        var row=valid(sh,false);
        return new Principal(Store.text(row,"user_id"),sh,sid,Store.number(row,"epoch"),(String)req.getAttribute("requestId"));
    }
    public Map<String,Object> valid(String hash,boolean lock) {
        var row=db.one("SELECT s.*,u.security_epoch,u.enterprise_status FROM portalsession s JOIN portaluser u ON u.id=s.user_id AND u.tenant_id=s.tenant_id JOIN systemconfig c ON c.tenant_id=s.tenant_id WHERE s.tenant_id=? AND s.sid_hash=? AND s.revoked_at IS NULL AND s.generation=? AND s.epoch=u.security_epoch AND u.enterprise_status<>'INACTIVE' AND UTC_TIMESTAMP(6)<TIMESTAMPADD(MINUTE,c.idle_minutes,s.last_activity) AND UTC_TIMESTAMP(6)<TIMESTAMPADD(HOUR,c.absolute_hours,s.created_at)"+(lock?" FOR UPDATE":""),Store.TENANT,hash,generation);
        if(row==null) {
            db.update("UPDATE portalsession SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(6)) WHERE sid_hash=?",hash);
            throw new ApiError(401,"AUTH_REQUIRED","会话已失效，请重新登录");
        }
        return row;
    }
    public void csrfCheck(HttpServletRequest req,Principal p) {
        origin(req);
        if(!Crypto.equal(csrf(p.sid()),req.getHeader("X-CSRF-Token"))) throw new ApiError(403,"CSRF_INVALID","CSRF校验失败");
    }
    public Map<String,Object> user(Principal p) { return db.one("SELECT * FROM portaluser WHERE tenant_id=? AND id=?",Store.TENANT,p.userId()); }
    public void role(Principal p,String...roles) {
        Set<String> actual=Set.of(Store.text(user(p),"roles").split(","));
        if(Arrays.stream(roles).noneMatch(actual::contains)) throw ApiError.forbidden();
    }
    public Map<String,Object> mapping(Principal p) {
        var m=db.one("SELECT * FROM usermapping WHERE tenant_id=? AND user_id=?",Store.TENANT,p.userId());
        if(m==null || !"MAPPED".equals(m.get("mapping_status")) || !"ACTIVE".equals(m.get("bi_status"))) throw new ApiError(403,"BI_MAPPING_REQUIRED","需要有效的 Quick BI 用户映射");
        return m;
    }
    public void activity(Principal p) { db.update("UPDATE portalsession SET last_activity=GREATEST(last_activity,UTC_TIMESTAMP(6)) WHERE sid_hash=? AND revoked_at IS NULL",p.sidHash()); }
    public Map<String,Object> me(Principal p) {
        var row=db.one("SELECT u.id userId,u.normalized_isid isid,u.display_name displayName,u.roles,m.mapping_status mappingStatus,m.quickbi_user_id quickbiUserId,m.version mappingVersion,LEAST(TIMESTAMPADD(MINUTE,c.idle_minutes,s.last_activity),TIMESTAMPADD(HOUR,c.absolute_hours,s.created_at)) sessionExpiresAt FROM portaluser u JOIN usermapping m ON m.user_id=u.id JOIN portalsession s ON s.user_id=u.id JOIN systemconfig c ON c.tenant_id=u.tenant_id WHERE u.tenant_id=? AND s.sid_hash=?",Store.TENANT,p.sidHash());
        var result=new LinkedHashMap<>(row); result.put("csrfToken",csrf(p.sid())); result.put("mode","mock"); return result;
    }
    public void logout(Principal p) {
        db.tx(()->{
            db.one("SELECT id FROM portaluser WHERE tenant_id=? AND id=? FOR UPDATE",Store.TENANT,p.userId());
            db.update("UPDATE portalsession SET revoked_at=UTC_TIMESTAMP(6) WHERE sid_hash=?",p.sidHash());
            audit.write(p.userId(),"LOGOUT","SUCCESS",null,p.requestId(),"Local session revoked"); return null;
        });
    }
}

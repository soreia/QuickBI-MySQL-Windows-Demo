package demo;
import org.springframework.stereotype.Component;
@Component
public class Audit {
    private final Store db;
    public volatile boolean simulateFailure=false;
    public Audit(Store db) { this.db=db; }
    public void write(String actor,String event,String result,String resource,String requestId,String details) {
        if(simulateFailure) throw new ApiError(503,"AUDIT_UNAVAILABLE","模拟审计接收失败：不交付新票据");
        db.update("INSERT INTO auditevent VALUES (?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",Store.id(),Store.TENANT,actor,event,result,resource,requestId,details);
    }
}

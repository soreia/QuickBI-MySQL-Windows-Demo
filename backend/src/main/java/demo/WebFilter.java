package demo;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
@Component
public class WebFilter extends OncePerRequestFilter {
    private final Auth auth; private final ObjectMapper json;
    public WebFilter(Auth auth,ObjectMapper json) { this.auth=auth; this.json=json; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String requestId=Store.id(); req.setAttribute("requestId",requestId);
        res.setHeader("X-Request-Id",requestId); res.setHeader("Cache-Control","no-store");
        res.setHeader("Referrer-Policy","no-referrer"); res.setHeader("X-Content-Type-Options","nosniff");
        res.setHeader("X-Frame-Options","SAMEORIGIN");
        res.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-src 'self'; frame-ancestors 'self'; base-uri 'none'; form-action 'self'");
        try {
            String path=req.getRequestURI();
            if(path.startsWith("/api/")) {
                Auth.Principal p=null;
                try { p=auth.authenticate(req); }
                catch(ApiError e) { if(!path.equals("/api/v1/auth/logout")) throw e; }
                if(!Set.of("GET","HEAD").contains(req.getMethod())) {
                    if(p!=null) auth.csrfCheck(req,p); else auth.origin(req);
                }
                req.setAttribute("principal",p);
            }
            chain.doFilter(req,res);
        } catch(ApiError e) { write(res,e.status,e.code,e.getMessage(),requestId); }
        catch(Exception e) { if(!res.isCommitted()) write(res,503,"LOCAL_STORAGE_UNAVAILABLE","本地依赖或持久化不可用；请检查服务日志与数据库",requestId); }
    }
    private void write(HttpServletResponse res,int status,String code,String message,String id) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(json.writeValueAsString(Map.of("requestId",id,"error",Map.of("code",code,"message",message,"retryable",status>=500,"details",List.of()))));
    }
}

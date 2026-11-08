package demo;
public class ApiError extends RuntimeException {
    public final int status;
    public final String code;
    public ApiError(int status,String code,String message) { super(message); this.status=status; this.code=code; }
    public static ApiError forbidden() { return new ApiError(403,"ROLE_FORBIDDEN","无权执行此操作"); }
    public static ApiError unavailable() { return new ApiError(503,"PROVIDER_UNAVAILABLE","模拟 Quick BI 不可用，已拒绝放行"); }
}

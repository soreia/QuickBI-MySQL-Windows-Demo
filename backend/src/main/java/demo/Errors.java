package demo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.format.DateTimeParseException;
import java.util.*;
@RestControllerAdvice
public class Errors {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handle(Exception e,HttpServletRequest r) {
        int status=503; String code="LOCAL_STORAGE_UNAVAILABLE",message="持久化或本地依赖异常，未放行新票据";
        if(e instanceof ApiError a) { status=a.status; code=a.code; message=a.getMessage(); }
        else if(e instanceof DuplicateKeyException) { status=409; code="MAPPING_CONFLICT"; message="唯一映射冲突，不能静默覆盖"; }
        else if(e instanceof HttpMessageNotReadableException || e instanceof MethodArgumentTypeMismatchException || e instanceof DateTimeParseException || e instanceof org.springframework.web.bind.MissingServletRequestParameterException) { status=400; code="INVALID_REQUEST"; message="请求参数格式错误或包含不允许字段"; }
        else if(e instanceof org.springframework.web.servlet.resource.NoResourceFoundException) { status=404; code="NOT_FOUND"; message="接口不存在"; }
        else if(e instanceof org.springframework.web.HttpRequestMethodNotSupportedException) { status=405; code="METHOD_NOT_ALLOWED"; message="不支持此请求方法"; }
        return ResponseEntity.status(status).body(Map.of("requestId",r.getAttribute("requestId"),"error",Map.of("code",code,"message",message,"retryable",status>=500,"details",List.of())));
    }
}

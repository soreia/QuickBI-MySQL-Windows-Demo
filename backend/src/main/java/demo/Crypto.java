package demo;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
public final class Crypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Crypto() {}
    public static String random() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    public static String hmac(String key,String text) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException("HMAC initialization failed"); }
    }
    public static boolean equal(String a,String b) { return a!=null && b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)); }
    public static String html(String text) { return text==null?"":text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
}

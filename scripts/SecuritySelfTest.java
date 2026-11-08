import demo.Crypto;
import java.util.HashSet;
public class SecuritySelfTest {
    public static void main(String[] args) {
        String key="local-self-test-key-at-least-32-characters";
        require(Crypto.hmac(key,"sid:test").matches("[a-f0-9]{64}"),"SHA-256 format");
        require(Crypto.hmac(key,"sid:test").equals(Crypto.hmac(key,"sid:test")),"deterministic");
        require(!Crypto.hmac(key,"sid:test").equals(Crypto.hmac(key,"csrf:test")),"domain separation");
        require(Crypto.equal("abc","abc") && !Crypto.equal("abc","abd") && !Crypto.equal(null,"abc"),"constant-time comparator contract");
        require(Crypto.html("<script>\"'&").equals("&lt;script&gt;&quot;&#39;&amp;"),"HTML encoding");
        var set=new HashSet<String>();
        for(int i=0;i<10000;i++) require(set.add(Crypto.random()),"random token duplicate");
        require(set.stream().allMatch(s->s.matches("[A-Za-z0-9_-]{43}")),"URL safe");
        System.out.println("PASS: HMAC format, determinism, domain separation, comparison, HTML encoding, 10000 random tokens");
    }
    private static void require(boolean condition,String name) { if(!condition)throw new AssertionError(name); }
}

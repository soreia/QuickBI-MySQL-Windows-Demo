package demo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CryptoTest {
    @Test void hmacIsSeparatedAndNotRawSid() {
        String key="local-junit-key-at-least-thirty-two-characters",sid=Crypto.random();
        assertEquals(64,Crypto.hmac(key,"sid:"+sid).length());
        assertNotEquals(sid,Crypto.hmac(key,"sid:"+sid));
        assertNotEquals(Crypto.hmac(key,"sid:"+sid),Crypto.hmac(key,"csrf:"+sid));
        assertFalse(Crypto.equal(null,"value"));
    }
    @Test void untrustedHtmlIsEncoded() { assertEquals("&lt;script&gt;",Crypto.html("<script>")); }
    @Test void queriesEscapeWildcardCharacters() { assertEquals("%a!%!_!!%",Portal.like("a%_!")); }
    @Test void invalidPageSizeIsRejected() { assertThrows(ApiError.class,()->Portal.size(101)); }
}

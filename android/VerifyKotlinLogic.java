import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URLDecoder;

/**
 * 逐行复刻 Android 端 Signer.kt / Crypto.kt 的算法，
 * 用项目 Python SDK 产出的向量做对照，验证 Kotlin 移植是否一致。
 */
public class VerifyKotlinLogic {

    static final String API_KEY = "17bf6ed3b808eb7dcfa5wa0f1f0cf1de";
    static final String SIGN_SECRET = "9bldwb2d5d02e81h";
    // Γ_Κ-ζ.Τfkst
    static final String PWD_SALT = "\u0393_\u039A-\u03B6.\u03A4fkst";

    static final String TEXT_TPL = "f0%scom.yaerxing.fkst%sF.K*$t";

    static int pass = 0, fail = 0;

    static String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            int v = b & 0xFF;
            if (v < 0x10) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    static String tail4(String v) {
        if (v == null) return "";
        return v.length() <= 4 ? v : v.substring(v.length() - 4);
    }

    static List<Map.Entry<String, String>> sorted(Map<String, String> p) {
        List<Map.Entry<String, String>> e = new ArrayList<>();
        for (Map.Entry<String, String> x : p.entrySet()) {
            if (!x.getKey().equals("api_sig")) e.add(x);
        }
        e.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        return e;
    }

    static String signParams(Map<String, String> p, String appC) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> x : sorted(p)) body.append(x.getKey()).append(x.getValue());
        body.append(SIGN_SECRET);
        String tpl = String.format(TEXT_TPL, tail4(p.get("call_id")), appC);
        body.append(md5(tpl).substring(5, 21));
        return md5(body.toString()).toUpperCase();
    }

    static String signComment(Map<String, String> p) throws Exception {
        String body = "api_key" + p.get("api_key")
                + "call_id" + p.get("call_id")
                + "openid" + p.get("openid")
                + SIGN_SECRET
                + md5(String.format(TEXT_TPL, tail4(p.get("call_id")), p.getOrDefault("app_c", "171")))
                    .substring(5, 21);
        return md5(body).toUpperCase();
    }

    static String encryptPassword(String password) throws Exception {
        return md5(password + PWD_SALT);
    }

    /** 等价于 Python urllib.parse.unquote */
    static String unquote(String s) throws Exception {
        return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
    }

    static String decryptContent(String cipher, String secretKey) throws Exception {
        String key = md5(secretKey + API_KEY).substring(8, 16).toLowerCase();
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] step1 = Base64.getMimeDecoder().decode(cipher);
        byte[] xor = new byte[step1.length];
        for (int i = 0; i < step1.length; i++) {
            xor[i] = (byte) (step1[i] ^ keyBytes[i % keyBytes.length]);
        }
        byte[] step3 = Base64.getMimeDecoder().decode(xor);
        return unquote(new String(step3, StandardCharsets.UTF_8));
    }

    static Map<String, String> deviceParams() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("api_key", API_KEY);
        m.put("appid", "wx2bd42ba7f4c547f5");
        m.put("app_c", "171");
        m.put("app_v", "2.0.2");
        m.put("channel", "none");
        m.put("platform_id", "2");
        m.put("device_imei", "f448c5eaf564af4dc63d5c0587e68290");
        m.put("rom", "OPPO");
        m.put("model", "PJJ110");
        m.put("brand", "OPPO");
        m.put("os_v", "29");
        m.put("oam", "0");
        m.put("url_name", "");
        m.put("device_token", "");
        m.put("identity", "171171dguf117cf2.a011f178egua59bd3dest.2194st1h");
        m.put("um_token", "AjyrWarcqPA-F-J60x70BmVVl8f0BWZzsx2WtcdvgSJm");
        return m;
    }

    static void check(String label, String actual, String expected) {
        boolean ok = actual.equals(expected);
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + label);
        if (!ok) {
            System.out.println("       期望: " + expected);
            System.out.println("       实际: " + actual);
        }
    }

    public static void main(String[] args) throws Exception {
        // ---- 1. 通用签名 ----
        Map<String, String> p = deviceParams();
        p.put("unionid", "guest");
        p.put("openid", "guest");
        p.put("mid", "1");
        p.put("type", "10");
        p.put("start_time", "0");
        p.put("page", "0");
        p.put("call_id", "1758760000000");
        check("通用签名 signParams", signParams(p, "171"),
                "C2C7939B3947D3D718C1EA8DF4C977D0");

        // ---- 2. 发评论签名 ----
        Map<String, String> c = deviceParams();
        c.put("unionid", "u1");
        c.put("openid", "o1");
        c.put("mid", "10000001");
        c.put("content", "awa");
        c.put("nid", "5127033");
        c.put("fid", "0");
        c.put("call_id", "1758760000000");
        check("评论签名 signComment", signComment(c),
                "8530FAEF66A4D6D00FB87ED32EA356AC");

        // ---- 3. 密码 MD5（含希腊字母盐）----
        check("密码加盐 MD5", encryptPassword("123456"),
                "406d3c22d933acab20c861828cfbf227");

        // ---- 4. 正文解密 ----
        Path vec = Path.of("vectors.txt");
        if (!Files.exists(vec)) {
            System.out.println("SKIP  未找到 vectors.txt，跳过解密对照");
        } else {
            String cipher = null, expectedPlain = null;
            for (String line : Files.readAllLines(vec, StandardCharsets.UTF_8)) {
                if (line.startsWith("cipher=")) cipher = line.substring(7);
                else if (line.startsWith("plain=")) expectedPlain = line.substring(6);
            }
            if (cipher != null && expectedPlain != null) {
                String got = decryptContent(cipher, "1234567");
                check("正文解密 decryptContent", got, expectedPlain);
            }
        }

        System.out.println();
        System.out.println("结果: " + pass + " 通过 / " + fail + " 失败");
        if (fail > 0) System.exit(1);
    }
}

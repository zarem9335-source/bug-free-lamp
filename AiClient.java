package com.ekts.app.ai;

import android.os.Handler;
import android.os.Looper;

import com.ekts.app.model.CircuitModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** تماس با API هوش مصنوعی + رسم آفلاین فارسی بدون API */
public class AiClient {

    public interface Callback {
        void onResult(Result<AiResult> result);
    }

    public static class Result<T> {
        public T value;
        public Exception error;
        public boolean isSuccess;

        public static <T> Result<T> success(T v) {
            Result<T> r = new Result<>();
            r.value = v;
            r.isSuccess = true;
            return r;
        }

        public static <T> Result<T> failure(Exception e) {
            Result<T> r = new Result<>();
            r.error = e;
            r.isSuccess = false;
            return r;
        }
    }

    public static class AiResult {
        public String title;
        public String explanationFa;
        public CircuitModel.Circuit circuit;
        public String providerFa;
        public boolean usedOffline;

        public AiResult(String title, String explanationFa, CircuitModel.Circuit circuit, String providerFa, boolean usedOffline) {
            this.title = title;
            this.explanationFa = explanationFa;
            this.circuit = circuit;
            this.providerFa = providerFa;
            this.usedOffline = usedOffline;
        }
    }

    public static final String SYSTEM_PROMPT =
            "You are an electrical control circuit designer, like EKTS.\n" +
            "The user writes in Persian. Answer in Persian.\n" +
            "You MUST output ONLY a JSON object, no markdown, no extra text.\n" +
            "Schema:\n" +
            "{\"title\":\"...\",\"explanation_fa\":\"کوتاه، فارسی، طرز کار مدار در 3-5 خط\",\"components\":[{\"id\":\"L1\",\"type\":\"POWER_L\",\"x\":80,\"y\":180,\"label\":\"L\"},{\"id\":\"S0\",\"type\":\"STOP_NC\",\"x\":230,\"y\":180,\"label\":\"S0\"},{\"id\":\"S1\",\"type\":\"START_NO\",\"x\":390,\"y\":180,\"label\":\"S1\"},{\"id\":\"KM1\",\"type\":\"CONTACTOR_COIL\",\"x\":580,\"y\":180,\"label\":\"KM1\"},{\"id\":\"N1\",\"type\":\"POWER_N\",\"x\":770,\"y\":180,\"label\":\"N\"}],\"wires\":[{\"from\":\"L1\",\"to\":\"S0\"},{\"from\":\"S0\",\"to\":\"S1\"},{\"from\":\"S1\",\"to\":\"KM1\"},{\"from\":\"KM1\",\"to\":\"N1\"}]}\n" +
            "Allowed type values ONLY: POWER_L, POWER_N, POWER_3PH, FUSE, SWITCH_3P, START_NO, STOP_NC, JOG_NO, SWITCH, CONTACTOR_COIL, RELAY_COIL, CONTACT_NO, CONTACT_NC, OVERLOAD_NC, TIMER_ON, TIMER_OFF, TIMER_CONTACT_NO, TIMER_CONTACT_NC, LIMIT_NO, LIMIT_NC, MOTOR, MOTOR_1PH, LAMP, BUZZER\n" +
            "Rules:\n" +
            "- x between 80 and 770, y between 120 and 700. Put control rung at y=180, power rung at y=480.\n" +
            "- Every contactor coil KM1 must have a NO contact with the same label KM1 as self-holding, in parallel with the START button.\n" +
            "- A motor or lamp must be fed through a NO contact of its contactor, from a POWER_L to POWER_N.\n" +
            "- Use unique ids. Labels for contacts must equal their coil label, e.g. KM1, KM2, KT.\n" +
            "- Timer components may have \"delaySec\": number of seconds, e.g. 5.\n" +
            "- Always include POWER_L at the start of each rung and POWER_N at the end.\n" +
            "- Keep the circuit small: 6 to 14 components.\n";

    /** رسم آفلاین بدون API */
    public static AiResult parseOffline(String prompt) {
        CircuitModel.Circuit circuit = CircuitModel.byKeyword(prompt);
        if (circuit == null) return null;
        String explanation;
        if (prompt.contains("ستاره") || prompt.contains("مثلث")) {
            explanation = "مدار ستاره-مثلث: با استارت، KM1 وصل می‌شود و موتور اول ستاره راه می‌افتد. بعد از حدود ۳ ثانیه تایمر KT عمل می‌کند، کنتاکت ستاره باز و کنتاکت مثلث بسته می‌شود.";
        } else if (prompt.contains("آسانسور") || prompt.contains("اسانسور")) {
            explanation = "مدار آسانسور: دکمه UP موتور را بالا می‌برد و دکمه DN پایین می‌آورد. لیمیت سوئیچ‌های بالا و پایین در انتهای مسیر مدار را قطع می‌کنند و دو جهت با اینترلاک قفل شده‌اند.";
        } else if (prompt.contains("گیت") || prompt.contains("درب")) {
            explanation = "درب اتوماتیک: OPEN موتور را برای باز شدن و CLOSE برای بسته شدن راه می‌اندازد. لیمیت سوئیچ انتهای باز و بسته، حرکت را متوقف می‌کند.";
        } else if (prompt.contains("پمپ")) {
            explanation = "پمپ آب با فلوتر: وقتی سطح آب پایین است فلوتر بسته می‌شود و پمپ روشن می‌شود؛ با پر شدن مخزن، فلوتر باز و پمپ خاموش می‌شود.";
        } else if (prompt.contains("ترتیبی") || prompt.contains("نوار نقاله")) {
            explanation = "سه موتور ترتیبی: موتور اول با استارت روشن می‌شود، بعد از تایمر KT1 موتور دوم و بعد از KT2 موتور سوم روشن می‌شود.";
        } else if (prompt.contains("چپ") || prompt.contains("راست")) {
            explanation = "مدار چپ‌گرد/راست‌گرد: دو بوبین KM1 و KM2 با اینترلاک NC همدیگر قفل شده‌اند تا هرگز هم‌زمان وصل نشوند.";
        } else if (prompt.contains("تایمر")) {
            explanation = "مدار تایمر: با استارت، تایمر KT برق‌دار می‌شود و بعد از ۳ ثانیه کنتاکت NO آن بسته می‌شود و لامپ روشن می‌شود.";
        } else if (prompt.contains("لامپ") || prompt.contains("چراغ")) {
            explanation = "مدار لامپ ساده: با کلید، لامپ روشن می‌شود. در حالت اجرا روی کلید ضربه بزن.";
        } else {
            explanation = "مدار راه‌انداز مستقیم: با استارت S1 بوبین KM1 وصل می‌شود، کنتاکت خودنگهدار KM1 آن را نگه می‌دارد و موتور از طریق کنتاکت اصلی KM1 روشن می‌شود. استاپ S0 یا بی‌متال F2 مدار را قطع می‌کند.";
        }
        return new AiResult(circuit.name, explanation, circuit, "آفلاین (بدون API)", true);
    }

    public static void generate(final String promptFa, final AiProvider provider,
                                final String baseUrl, final String apiKey, final String model,
                                final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (apiKey == null || apiKey.trim().isEmpty()
                            || baseUrl == null || baseUrl.trim().isEmpty()) {
                        AiResult offline = parseOffline(promptFa);
                        if (offline != null) {
                            final AiResult r = offline;
                            main.post(new Runnable() {
                                @Override public void run() { callback.onResult(Result.success(r)); }
                            });
                        } else {
                            final Exception e = new IllegalStateException(
                                    "کلید API یا Base URL وارد نشده و دستور هم جزو مدارهای آماده آفلاین نیست. "
                                    + "در صفحه هوش مصنوعی، ارائه‌دهنده را انتخاب کن و کلید را وارد کن، "
                                    + "یا یکی از این‌ها را بگو: «راه‌انداز مستقیم موتور»، «ستاره مثلث»، «آسانسور»، «درب اتوماتیک»، «پمپ آب».");
                            main.post(new Runnable() {
                                @Override public void run() { callback.onResult(Result.failure(e)); }
                            });
                        }
                        return;
                    }

                    String endpoint = baseUrl.trim();
                    if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
                    endpoint += "/chat/completions";
                    URL url = new URL(endpoint);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(60000);
                    conn.setDoOutput(true);

                    JSONObject body = new JSONObject();
                    body.put("model", model.trim());
                    body.put("temperature", 0.2);
                    JSONArray messages = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", SYSTEM_PROMPT);
                    messages.put(sys);
                    JSONObject user = new JSONObject();
                    user.put("role", "user");
                    user.put("content", promptFa);
                    messages.put(user);
                    body.put("messages", messages);

                    OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
                    writer.write(body.toString());
                    writer.flush();
                    writer.close();

                    int code = conn.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                            StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();
                    String text = sb.toString();

                    if (code < 200 || code >= 300) {
                        throw new IllegalStateException("خطا از " + provider.faName + " (کد " + code + "): " + text.substring(0, Math.min(400, text.length())));
                    }

                    String content = new JSONObject(text)
                            .getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").optString("content", "");
                    String jsonText = extractJson(content);
                    if (jsonText == null) {
                        throw new IllegalStateException("پاسخ هوش مصنوعی JSON مدار نداشت. متن پاسخ: " + content.substring(0, Math.min(300, content.length())));
                    }
                    JSONObject parsed = new JSONObject(jsonText);
                    String title = parsed.optString("title", "مدار هوش مصنوعی");
                    String explanation = parsed.optString("explanation_fa", "");
                    CircuitModel.Circuit circuit = CircuitModel.CircuitJson.fromJson(jsonText);
                    if (circuit == null) {
                        throw new IllegalStateException("JSON برگشتی قابل رسم نبود.");
                    }
                    if (explanation.isEmpty()) {
                        explanation = "مدار «" + title + "» رسم شد. در صفحه ویرایشگر، دکمه اجرا را بزن و شستی استارت را نگه دار.";
                    }
                    final AiResult result = new AiResult(title, explanation, circuit, provider.faName, false);
                    main.post(new Runnable() {
                        @Override public void run() { callback.onResult(Result.success(result)); }
                    });
                } catch (final Exception e) {
                    AiResult offline = parseOffline(promptFa);
                    if (offline != null) {
                        final AiResult r = new AiResult(offline.title,
                                offline.explanationFa + "\n\n(توجه: اتصال به " + provider.faName + " خطا داد: " + e.getMessage() + " — این مدار به‌صورت آفلاین رسم شد.)",
                                offline.circuit, offline.providerFa, true);
                        main.post(new Runnable() {
                            @Override public void run() { callback.onResult(Result.success(r)); }
                        });
                    } else {
                        main.post(new Runnable() {
                            @Override public void run() { callback.onResult(Result.failure(e)); }
                        });
                    }
                }
            }
        }).start();
    }

    public static String extractJson(String text) {
        if (text == null) return null;
        String t = text.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return t.substring(start, end + 1);
    }
}

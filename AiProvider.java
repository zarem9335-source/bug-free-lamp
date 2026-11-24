package com.ekts.app.ai;

/**
 * ارائه‌دهنده‌های هوش مصنوعی — همه OpenAI-Compatible
 * لیارا: Base URL مخصوص Workspace است — از کنسول لیارا کپی می‌شود.
 * اوا (AvalAI): https://api.avalai.ir/v1 — داخل کشور: https://api.avalapis.ir/v1
 */
public enum AiProvider {
    AVALAI(
            "اوا — AvalAI",
            "https://api.avalai.ir/v1",
            "gpt-4o-mini",
            "داخل ایران اگر api.avalai.ir باز نشد، از https://api.avalapis.ir/v1 استفاده کن.",
            "کلید API اوا (AvalAI)"
    ),
    LIARA(
            "لیارا — Liara AI",
            "",
            "google/gemini-2.5-flash",
            "Base URL را دقیقاً از کنسول لیارا کپی کن (آدرس سرویس هوش مصنوعی + مسیر Workspace).",
            "کلید API لیارا"
    ),
    OPENAI(
            "OpenAI",
            "https://api.openai.com/v1",
            "gpt-4o-mini",
            "کلید OpenAI مستقیم — در ایران معمولاً نیاز به VPN دارد.",
            "کلید API اوپن‌ای‌آی"
    ),
    CUSTOM(
            "سفارشی (OpenAI-Compatible)",
            "",
            "gpt-4o-mini",
            "هر سرویسی که با OpenAI سازگار است: Base URL خودش را وارد کن.",
            "کلید API"
    );

    public final String faName;
    public final String defaultBaseUrl;
    public final String defaultModel;
    public final String hintFa;
    public final String keyLabelFa;

    AiProvider(String faName, String defaultBaseUrl, String defaultModel, String hintFa, String keyLabelFa) {
        this.faName = faName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.hintFa = hintFa;
        this.keyLabelFa = keyLabelFa;
    }

    public static AiProvider fromName(String name) {
        if (name == null) return AVALAI;
        for (AiProvider p : values()) {
            if (p.name().equals(name)) return p;
        }
        return AVALAI;
    }
}

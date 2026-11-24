package com.ekts.app.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ekts.app.R;
import com.ekts.app.model.CircuitModel;
import com.ekts.app.ui.MainActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * صفحه جدا برای هوش مصنوعی — جاوا
 *
 * ذخیره API: کلید، Base URL و مدل برای هر ارائه‌دهنده (اوا / لیارا / OpenAI / سفارشی)
 * جدا در SharedPreferences ذخیره می‌شود و با باز شدن صفحه خودکار برمی‌گردد —
 * لازم نیست هر بار دوباره وارد کنی. ذخیره با دکمه «ذخیره تنظیمات»،
 * با دکمه ارسال، و با خروج از صفحه هم انجام می‌شود.
 * کلید فقط روی گوشی خودت می‌ماند و هیچ‌وقت به گیت‌هاب یا سرور دیگری فرستاده نمی‌شود
 * (فقط موقع درخواست به همان ارائه‌دهنده‌ای که انتخاب کرده‌ای).
 */
public class AiActivity extends AppCompatActivity {

    private Spinner spProvider;
    private TextView tvHint;
    private TextView tvSaved;
    private EditText etBaseUrl;
    private EditText etModel;
    private EditText etApiKey;
    private EditText etPrompt;
    private TextView tvAnswer;
    private Button btnDraw;
    private Button btnSend;

    private AiProvider currentProvider = AiProvider.AVALAI;
    private String lastCircuitJson = null;
    private String lastTitle = "";
    private boolean keyVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai);

        spProvider = findViewById(R.id.spProvider);
        tvHint = findViewById(R.id.tvProviderHint);
        tvSaved = findViewById(R.id.tvSavedStatus);
        etBaseUrl = findViewById(R.id.etBaseUrl);
        etModel = findViewById(R.id.etModel);
        etApiKey = findViewById(R.id.etApiKey);
        etPrompt = findViewById(R.id.etPrompt);
        tvAnswer = findViewById(R.id.tvAnswer);
        btnDraw = findViewById(R.id.btnDraw);
        btnSend = findViewById(R.id.btnSend);

        List<String> names = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) names.add(p.faName);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        spProvider.setAdapter(adapter);
        spProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                currentProvider = AiProvider.values()[pos];
                loadSettingsFor(currentProvider);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.btnSaveSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                Toast.makeText(AiActivity.this, "ذخیره شد — دفعه بعد لازم نیست دوباره وارد کنی", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnToggleKey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keyVisible = !keyVisible;
                if (keyVisible) {
                    etApiKey.setTransformationMethod(null);
                    ((Button) v).setText("پنهان کن");
                } else {
                    etApiKey.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    ((Button) v).setText("نمایش کلید");
                }
                etApiKey.setSelection(etApiKey.getText().length());
            }
        });

        findViewById(R.id.btnClearKey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etApiKey.setText("");
                saveSettings();
                Toast.makeText(AiActivity.this, "کلید این ارائه‌دهنده پاک شد", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                tvAnswer.setText("در حال تست اتصال به " + currentProvider.faName + " …");
                AiClient.generate("مدار لامپ ساده رسم کن", currentProvider,
                        etBaseUrl.getText().toString(), etApiKey.getText().toString(),
                        etModel.getText().toString().trim().isEmpty() ? currentProvider.defaultModel : etModel.getText().toString(),
                        new AiClient.Callback() {
                            @Override
                            public void onResult(AiClient.Result<AiClient.AiResult> result) {
                                if (result.isSuccess) {
                                    tvAnswer.setText("اتصال برقرار است ✓ (" + currentProvider.faName + ")\n\n" + result.value.explanationFa);
                                } else {
                                    tvAnswer.setText("تست ناموفق بود: " + result.error.getMessage());
                                }
                            }
                        });
            }
        });

        findViewById(R.id.btnExample1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { etPrompt.setText("مدار ستاره مثلث رسم کن"); }
        });
        findViewById(R.id.btnExample2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { etPrompt.setText("راه‌انداز مستقیم موتور با شستی استارت و استاپ و خودنگهدار رسم کن"); }
        });
        findViewById(R.id.btnExample3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { etPrompt.setText("مدار تایمر تاخیر در وصل با لامپ رسم کن"); }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String prompt = etPrompt.getText().toString().trim();
                if (prompt.isEmpty()) {
                    Toast.makeText(AiActivity.this, "اول دستور را بنویس", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveSettings(); // هر بار ارسال هم ذخیره می‌شود تا کلید گم نشود
                btnSend.setEnabled(false);
                btnDraw.setVisibility(View.GONE);
                tvAnswer.setText("در حال پرسیدن از " + currentProvider.faName + " …");
                AiClient.generate(prompt, currentProvider,
                        etBaseUrl.getText().toString(), etApiKey.getText().toString(),
                        etModel.getText().toString().trim().isEmpty() ? currentProvider.defaultModel : etModel.getText().toString(),
                        new AiClient.Callback() {
                            @Override
                            public void onResult(AiClient.Result<AiClient.AiResult> result) {
                                btnSend.setEnabled(true);
                                if (result.isSuccess) {
                                    AiClient.AiResult ai = result.value;
                                    lastTitle = ai.title;
                                    lastCircuitJson = ai.circuit == null ? null : CircuitModel.CircuitJson.toJson(ai.circuit);
                                    String source = ai.usedOffline ? "آفلاین" : ai.providerFa;
                                    tvAnswer.setText("«" + ai.title + "» (" + source + ")\n\n" + ai.explanationFa);
                                    btnDraw.setVisibility(ai.circuit != null ? View.VISIBLE : View.GONE);
                                } else {
                                    tvAnswer.setText("خطا: " + result.error.getMessage());
                                    Toast.makeText(AiActivity.this, "خطا: " + result.error.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });

        btnDraw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastCircuitJson == null) return;
                Intent intent = new Intent(AiActivity.this, MainActivity.class);
                intent.putExtra("circuit_json", lastCircuitJson);
                intent.putExtra("circuit_name", lastTitle);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });

        // ارائه‌دهنده آخری که استفاده کرده‌ای را برگردان
        String lastProvider = prefs().getString("provider", AiProvider.AVALAI.name());
        AiProvider last = AiProvider.fromName(lastProvider);
        int pos = 0;
        AiProvider[] all = AiProvider.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == last) pos = i;
        }
        spProvider.setSelection(pos);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings(); // خروج از صفحه هم ذخیره می‌شود
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("ekts_ai", MODE_PRIVATE);
    }

    private void loadSettingsFor(AiProvider provider) {
        SharedPreferences p = prefs();
        String base = p.getString("base_" + provider.name(), provider.defaultBaseUrl);
        String model = p.getString("model_" + provider.name(), provider.defaultModel);
        String key = p.getString("key_" + provider.name(), "");
        etBaseUrl.setText(base == null ? provider.defaultBaseUrl : base);
        etModel.setText(model == null ? provider.defaultModel : model);
        etApiKey.setText(key == null ? "" : key);
        tvHint.setText(provider.hintFa);
        etBaseUrl.setHint(provider == AiProvider.LIARA
                ? "Base URL لیارا را از کنسول لیارا کپی کن"
                : "Base URL — " + provider.defaultBaseUrl);
        updateSavedLabel(provider);
    }

    private void updateSavedLabel(AiProvider provider) {
        long savedAt = prefs().getLong("saved_at_" + provider.name(), 0);
        String key = prefs().getString("key_" + provider.name(), "");
        if (key != null && !key.isEmpty() && savedAt > 0) {
            String date = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                    .format(new Date(savedAt));
            tvSaved.setText("✓ تنظیمات " + provider.faName + " ذخیره شده (" + date + ") — لازم نیست دوباره وارد کنی");
        } else if (key != null && !key.isEmpty()) {
            tvSaved.setText("✓ کلید " + provider.faName + " ذخیره شده است");
        } else {
            tvSaved.setText("هنوز کلیدی برای " + provider.faName + " ذخیره نشده — یک بار وارد کن، برای همیشه می‌ماند");
        }
    }

    private void saveSettings() {
        String model = etModel.getText().toString().trim();
        if (model.isEmpty()) model = currentProvider.defaultModel;
        prefs().edit()
                .putString("base_" + currentProvider.name(), etBaseUrl.getText().toString().trim())
                .putString("model_" + currentProvider.name(), model)
                .putString("key_" + currentProvider.name(), etApiKey.getText().toString().trim())
                .putString("provider", currentProvider.name())
                .putLong("saved_at_" + currentProvider.name(), System.currentTimeMillis())
                .apply();
        updateSavedLabel(currentProvider);
    }
}

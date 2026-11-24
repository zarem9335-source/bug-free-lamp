package com.ekts.app.ui;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ekts.app.R;
import com.ekts.app.ai.AiActivity;
import com.ekts.app.model.CircuitModel;
import com.ekts.app.model.CircuitValidator;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * صفحه اصلی — جاوا — ویرایشگر مدار فرمان
 * آسان‌تر از ETKS: جای‌گذاری با ضربه روی بوم، چسبیدن به شبکه، جستجوی قطعه،
 * Undo/Redo، راهنمای قدم‌به‌قدم، بررسی خطا، لیست قطعات، خروجی PNG.
 */
public class MainActivity extends AppCompatActivity {

    private CircuitView circuitView;
    private TextView tvStatus;
    private TextView tvTitle;
    private Button btnRun;
    private LinearLayout palette;
    private EditText etSearch;

    private String lastPickedId = null;
    private String secondPickedId = null;

    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private final ArrayDeque<String> redoStack = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        circuitView = findViewById(R.id.circuitView);
        tvStatus = findViewById(R.id.tvStatus);
        tvTitle = findViewById(R.id.tvTitle);
        btnRun = findViewById(R.id.btnRun);
        palette = findViewById(R.id.palette);
        etSearch = findViewById(R.id.etSearch);

        circuitView.onStatus = new CircuitView.StatusListener() {
            @Override public void onStatus(String status) { tvStatus.setText(status); }
        };
        circuitView.onSelection = new CircuitView.SelectionListener() {
            @Override public void onSelection(CircuitModel.CircuitComponent comp) {
                if (comp == null) {
                    lastPickedId = null;
                    secondPickedId = null;
                } else {
                    if (lastPickedId == null) lastPickedId = comp.id;
                    else if (!lastPickedId.equals(comp.id)) secondPickedId = comp.id;
                    tvStatus.setText("انتخاب: " + comp.label + " — " + comp.type.fa);
                }
            }
        };
        circuitView.onPlaced = new CircuitView.PlaceListener() {
            @Override public void onPlaced(CircuitModel.CircuitComponent comp) {
                tvStatus.setText("قرار گرفت: " + comp.label + " — " + comp.type.fa);
                Toast.makeText(MainActivity.this, comp.type.fa + " قرار گرفت", Toast.LENGTH_SHORT).show();
            }
        };

        String aiJson = getIntent().getStringExtra("circuit_json");
        String aiName = getIntent().getStringExtra("circuit_name");
        CircuitModel.Circuit loaded = null;
        if (aiJson != null && !aiJson.isEmpty()) loaded = CircuitModel.CircuitJson.fromJson(aiJson);
        if (loaded == null) loaded = loadSaved();
        if (loaded == null) loaded = CircuitModel.dolStarter();
        circuitView.setCircuit(loaded);
        tvTitle.setText("EKTS — " + loaded.name);
        if (aiName != null && !aiName.isEmpty()) {
            Toast.makeText(this, "مدار «" + aiName + "» رسم شد", Toast.LENGTH_LONG).show();
        }

        buildPalette("");
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { buildPalette(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleRun(); }
        });
        findViewById(R.id.btnAi).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, AiActivity.class)); }
        });
        findViewById(R.id.btnExamples).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showExamples(v); }
        });
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveCurrent();
                Toast.makeText(MainActivity.this, "ذخیره شد", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btnDelete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pushUndo(); circuitView.deleteSelected(); }
        });
        findViewById(R.id.btnClear).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pushUndo();
                circuitView.stopSimulation();
                btnRun.setText(R.string.run);
                circuitView.setCircuit(new CircuitModel.Circuit("مدار جدید"));
                tvTitle.setText("EKTS — مدار جدید");
            }
        });
        findViewById(R.id.btnUndo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { undo(); }
        });
        findViewById(R.id.btnRedo).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { redo(); }
        });
        findViewById(R.id.btnRotate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (circuitView.selectedComponent() == null) {
                    Toast.makeText(MainActivity.this, "اول یک قطعه را انتخاب کن", Toast.LENGTH_SHORT).show();
                    return;
                }
                pushUndo();
                circuitView.rotateSelected();
            }
        });
        findViewById(R.id.btnProps).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showProperties(); }
        });
        findViewById(R.id.btnCheck).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showErrors(); }
        });
        findViewById(R.id.btnBom).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showBom(); }
        });
        findViewById(R.id.btnExport).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { exportPng(); }
        });
        findViewById(R.id.btnMyCircuits).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMyCircuits(); }
        });
        findViewById(R.id.btnCalc).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, CalculatorActivity.class)); }
        });
        findViewById(R.id.btnHelp).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHelp(); }
        });
        findViewById(R.id.btnZoomIn).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { circuitView.setZoom(circuitView.getZoom() + 0.15f); }
        });
        findViewById(R.id.btnZoomOut).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { circuitView.setZoom(circuitView.getZoom() - 0.15f); }
        });
        findViewById(R.id.btnWire).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (lastPickedId == null || secondPickedId == null) {
                    Toast.makeText(MainActivity.this, "اول دو قطعه را پشت سر هم انتخاب کن (روی هر کدام یک ضربه)", Toast.LENGTH_LONG).show();
                    return;
                }
                pushUndo();
                if (circuitView.wireBetween(lastPickedId, secondPickedId)) {
                    Toast.makeText(MainActivity.this, "سیم کشیده شد: " + lastPickedId + " ←→ " + secondPickedId, Toast.LENGTH_SHORT).show();
                    lastPickedId = null;
                    secondPickedId = null;
                } else {
                    Toast.makeText(MainActivity.this, "این سیم قبلاً هست یا قطعه‌ها یکی‌اند", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // راهنمای اولین بار
        SharedPreferences prefs = getSharedPreferences("ekts", MODE_PRIVATE);
        if (!prefs.getBoolean("help_shown", false)) {
            prefs.edit().putBoolean("help_shown", true).apply();
            showHelp();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String json = intent.getStringExtra("circuit_json");
        if (json == null) return;
        CircuitModel.Circuit circuit = CircuitModel.CircuitJson.fromJson(json);
        if (circuit == null) return;
        pushUndo();
        circuitView.stopSimulation();
        btnRun.setText(R.string.run);
        circuitView.setCircuit(circuit);
        tvTitle.setText("EKTS — " + circuit.name);
        Toast.makeText(this, "مدار «" + circuit.name + "» رسم شد", Toast.LENGTH_LONG).show();
    }

    private void toggleRun() {
        if (circuitView.isRunning()) {
            circuitView.stopSimulation();
            btnRun.setText(R.string.run);
            tvStatus.setText(R.string.status_ready);
        } else {
            List<CircuitValidator.Issue> issues = CircuitValidator.validate(circuitView.getCircuit());
            boolean hasError = false;
            for (CircuitValidator.Issue i : issues) if (i.isError) hasError = true;
            if (hasError) {
                showErrors();
                Toast.makeText(this, "اول خطاهای مدار را درست کن", Toast.LENGTH_LONG).show();
                return;
            }
            circuitView.startSimulation();
            btnRun.setText(R.string.stop);
            tvStatus.setText("در حال اجرا — شستی استارت را نگه دار");
        }
    }

    private void buildPalette(String query) {
        palette.removeAllViews();
        String q = query == null ? "" : query.trim();
        for (final CircuitModel.ComponentType type : CircuitModel.ComponentType.values()) {
            if (!q.isEmpty() && !type.fa.contains(q) && !type.category.contains(q)) continue;
            Button b = new Button(this);
            b.setText(type.fa);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (circuitView.isRunning()) {
                        Toast.makeText(MainActivity.this, "اول توقف را بزن، بعد قطعه اضافه کن", Toast.LENGTH_SHORT).show();
                    } else {
                        pushUndo();
                        circuitView.setPendingType(type);
                        tvStatus.setText(type.fa + " انتخاب شد — حالا روی بوم ضربه بزن تا همان‌جا قرار بگیرد");
                        Toast.makeText(MainActivity.this, "روی بوم ضربه بزن تا " + type.fa + " قرار بگیرد", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            palette.addView(b);
        }
        if (palette.getChildCount() == 0) {
            TextView t = new TextView(this);
            t.setText("قطعه‌ای پیدا نشد — مثلا «تایمر» یا «موتور» را جستجو کن");
            palette.addView(t);
        }
    }

    private void showExamples(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        final List<CircuitModel.Circuit> items = CircuitModel.all();
        for (int i = 0; i < items.size(); i++) popup.getMenu().add(0, i, i, items.get(i).name);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                CircuitModel.Circuit c = items.get(item.getItemId());
                pushUndo();
                circuitView.stopSimulation();
                btnRun.setText(R.string.run);
                circuitView.setCircuit(c);
                tvTitle.setText("EKTS — " + c.name);
                return true;
            }
        });
        popup.show();
    }

    private void pushUndo() {
        undoStack.push(CircuitModel.CircuitJson.toJson(circuitView.getCircuit()));
        if (undoStack.size() > 50) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "چیزی برای برگرداندن نیست", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.push(CircuitModel.CircuitJson.toJson(circuitView.getCircuit()));
        CircuitModel.Circuit c = CircuitModel.CircuitJson.fromJson(undoStack.pop());
        if (c != null) circuitView.setCircuit(c);
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "چیزی برای جلو رفتن نیست", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.push(CircuitModel.CircuitJson.toJson(circuitView.getCircuit()));
        CircuitModel.Circuit c = CircuitModel.CircuitJson.fromJson(redoStack.pop());
        if (c != null) circuitView.setCircuit(c);
    }

    private void showProperties() {
        final CircuitModel.CircuitComponent comp = circuitView.selectedComponent();
        if (comp == null) {
            Toast.makeText(this, "اول یک قطعه را انتخاب کن", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        final EditText etLabel = new EditText(this);
        etLabel.setText(comp.label);
        etLabel.setHint("نام قطعه — مثلا KM1");
        layout.addView(etLabel);
        final EditText etDelay = new EditText(this);
        etDelay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etDelay.setText(String.valueOf(comp.delaySec));
        etDelay.setHint("زمان تایمر (ثانیه) — فقط برای تایمر");
        layout.addView(etDelay);
        final Spinner spColor = new Spinner(this);
        String[] colors = new String[]{"پیش‌فرض", "قرمز", "سبز", "زرد", "آبی"};
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, colors);
        spColor.setAdapter(ad);
        int sel = 0;
        for (int i = 0; i < colors.length; i++) if (colors[i].equals(comp.color)) sel = i;
        spColor.setSelection(sel);
        layout.addView(spColor);

        new AlertDialog.Builder(this)
                .setTitle("مشخصات: " + comp.type.fa)
                .setView(layout)
                .setPositiveButton("تایید", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        pushUndo();
                        String label = etLabel.getText().toString().trim();
                        if (!label.isEmpty()) comp.label = label;
                        try {
                            float delay = Float.parseFloat(etDelay.getText().toString().trim());
                            if (delay >= 0.5f && delay <= 60f) comp.delaySec = delay;
                        } catch (Exception ignored) {}
                        String color = (String) spColor.getSelectedItem();
                        comp.color = "پیش‌فرض".equals(color) ? "" : color;
                        circuitView.invalidate();
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void showErrors() {
        List<CircuitValidator.Issue> issues = CircuitValidator.validate(circuitView.getCircuit());
        StringBuilder sb = new StringBuilder();
        for (CircuitValidator.Issue i : issues) {
            sb.append(i.isError ? "⛔ " : "⚠ ").append(i.textFa).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("بررسی خطا")
                .setMessage(sb.toString().trim())
                .setPositiveButton("باشه", null)
                .show();
    }

    private void showBom() {
        new AlertDialog.Builder(this)
                .setTitle("لیست قطعات (BOM)")
                .setMessage(CircuitValidator.billOfMaterials(circuitView.getCircuit()))
                .setPositiveButton("باشه", null)
                .show();
    }

    private void exportPng() {
        try {
            Bitmap bmp = circuitView.exportBitmap();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "ekts-" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EKTS");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, "ذخیره عکس نشد", Toast.LENGTH_SHORT).show();
                return;
            }
            OutputStream out = getContentResolver().openOutputStream(uri);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            if (out != null) out.close();
            Toast.makeText(this, "عکس مدار در گالری > EKTS ذخیره شد", Toast.LENGTH_LONG).show();
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "اشتراک عکس مدار"));
        } catch (Exception e) {
            Toast.makeText(this, "خطا در خروجی PNG: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showMyCircuits() {
        final SharedPreferences prefs = getSharedPreferences("ekts_circuits", MODE_PRIVATE);
        final List<String> names = new ArrayList<>();
        try {
            JSONObject obj = new JSONObject(prefs.getString("map", "{}"));
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) names.add(keys.next());
        } catch (Exception ignored) {}

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        final EditText etName = new EditText(this);
        etName.setText(circuitView.getCircuit().name);
        etName.setHint("نام مدار برای ذخیره");
        layout.addView(etName);

        new AlertDialog.Builder(this)
                .setTitle("مدارهای من")
                .setView(layout)
                .setPositiveButton("ذخیره با این نام", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) name = circuitView.getCircuit().name;
                        try {
                            JSONObject obj = new JSONObject(prefs.getString("map", "{}"));
                            obj.put(name, CircuitModel.CircuitJson.toJson(circuitView.getCircuit()));
                            prefs.edit().putString("map", obj.toString()).apply();
                            Toast.makeText(MainActivity.this, "مدار «" + name + "» ذخیره شد", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "خطا در ذخیره", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("باز کردن…", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (names.isEmpty()) {
                            Toast.makeText(MainActivity.this, "هنوز مداری ذخیره نکرده‌ای", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("یک مدار را انتخاب کن")
                                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dd, int which) {
                                        try {
                                            JSONObject obj = new JSONObject(prefs.getString("map", "{}"));
                                            String json = obj.optString(names.get(which), "");
                                            CircuitModel.Circuit c = CircuitModel.CircuitJson.fromJson(json);
                                            if (c != null) {
                                                pushUndo();
                                                circuitView.setCircuit(c);
                                                tvTitle.setText("EKTS — " + c.name);
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                })
                                .show();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showHelp() {
        String help = "۱. از پایین صفحه یک قطعه را بزن، بعد روی بوم ضربه بزن تا همان‌جا قرار بگیرد.\n\n"
                + "۲. قطعه را بکش تا جابه‌جا شود (به شبکه می‌چسبد). روی قطعه ضربه بزن تا انتخاب شود؛ بعد «چرخش» یا «مشخصات» را بزن.\n\n"
                + "۳. برای سیم‌کشی، دو قطعه را پشت سر هم انتخاب کن و «سیم بین دو قطعه» را بزن.\n\n"
                + "۴. «بررسی خطا» را بزن تا اتصال کوتاه و کنتاکت بدون بوبین را پیدا کند.\n\n"
                + "۵. «اجرا» را بزن و شستی استارت را نگه دار. موتور و لامپ روشن می‌شوند و جریان با نقطه‌های زرد روی سیم‌ها دیده می‌شود.\n\n"
                + "۶. با 🤖 هوش مصنوعی، دستور فارسی بده و مدار را رسم کن. کلید API یک بار وارد می‌شود و ذخیره می‌ماند.\n\n"
                + "۷. با «مدارهای من» چند مدار را با نام ذخیره کن و با PNG عکس مدار را بگیر.";
        new AlertDialog.Builder(this)
                .setTitle("راهنمای سریع")
                .setMessage(help)
                .setPositiveButton("باشه", null)
                .show();
    }

    private void saveCurrent() {
        SharedPreferences prefs = getSharedPreferences("ekts", MODE_PRIVATE);
        prefs.edit().putString("last_circuit", CircuitModel.CircuitJson.toJson(circuitView.getCircuit())).apply();
    }

    private CircuitModel.Circuit loadSaved() {
        SharedPreferences prefs = getSharedPreferences("ekts", MODE_PRIVATE);
        String json = prefs.getString("last_circuit", null);
        if (json == null) return null;
        return CircuitModel.CircuitJson.fromJson(json);
    }
}

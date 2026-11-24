package com.ekts.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ekts.app.R;

/** ماشین‌حساب — پیشرفته‌تر از ETKS: قانون اهم + توان موتور سه‌فاز */
public class CalculatorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        final EditText etV = findViewById(R.id.etVolt);
        final EditText etI = findViewById(R.id.etAmp);
        final EditText etR = findViewById(R.id.etOhm);
        final TextView tvOhm = findViewById(R.id.tvOhmResult);
        findViewById(R.id.btnOhm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String vs = etV.getText().toString().trim();
                    String is = etI.getText().toString().trim();
                    String rs = etR.getText().toString().trim();
                    if (!vs.isEmpty() && !is.isEmpty()) {
                        double volt = Double.parseDouble(vs);
                        double amp = Double.parseDouble(is);
                        tvOhm.setText("R = V / I = " + round(volt / amp) + " Ω\nP = V × I = " + round(volt * amp) + " W");
                    } else if (!vs.isEmpty() && !rs.isEmpty()) {
                        double volt = Double.parseDouble(vs);
                        double ohm = Double.parseDouble(rs);
                        tvOhm.setText("I = V / R = " + round(volt / ohm) + " A\nP = V² / R = " + round(volt * volt / ohm) + " W");
                    } else if (!is.isEmpty() && !rs.isEmpty()) {
                        double amp = Double.parseDouble(is);
                        double ohm = Double.parseDouble(rs);
                        tvOhm.setText("V = I × R = " + round(amp * ohm) + " V\nP = I² × R = " + round(amp * amp * ohm) + " W");
                    } else {
                        Toast.makeText(CalculatorActivity.this, "دو تا از V و I و R را وارد کن", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(CalculatorActivity.this, "عددها را درست وارد کن", Toast.LENGTH_SHORT).show();
                }
            }
        });

        final EditText etMv = findViewById(R.id.etMotorVolt);
        final EditText etMi = findViewById(R.id.etMotorAmp);
        final EditText etCos = findViewById(R.id.etCos);
        final TextView tvMotor = findViewById(R.id.tvMotorResult);
        findViewById(R.id.btnMotor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    double volt = Double.parseDouble(etMv.getText().toString().trim());
                    double amp = Double.parseDouble(etMi.getText().toString().trim());
                    String cosS = etCos.getText().toString().trim();
                    double cos = cosS.isEmpty() ? 0.85 : Double.parseDouble(cosS);
                    double p = Math.sqrt(3) * volt * amp * cos;
                    tvMotor.setText("P = √3 × V × I × cosφ = " + round(p) + " W  (" + round(p / 1000) + " kW)\n"
                            + "جریان ستاره-مثلث در لحظه راه‌اندازی حدود ۱/۳ جریان مستقیم است.");
                } catch (Exception e) {
                    Toast.makeText(CalculatorActivity.this, "ولتاژ و جریان موتور را وارد کن", Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.btnCalcBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
    }

    private String round(double d) {
        return String.format(java.util.Locale.US, "%.2f", d);
    }
}

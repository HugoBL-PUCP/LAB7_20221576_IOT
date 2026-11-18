package com.example.lab6_20221576;

import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lab6_20221576.databinding.ActivityForgotPasswordBinding;
import com.example.lab6_20221576.util.AuthErrors;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnEnviar.setOnClickListener(v -> enviarEnlace());
        b.tvVolver.setOnClickListener(v -> finish());
    }

    private void enviarEnlace() {
        String correo = b.etCorreo.getText() == null ? "" : b.etCorreo.getText().toString().trim();
        b.tilCorreo.setError(null);

        if (correo.isEmpty()) {
            b.tilCorreo.setError(getString(R.string.err_email_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            b.tilCorreo.setError(getString(R.string.err_email_invalid));
            return;
        }

        b.progress.setVisibility(View.VISIBLE);
        b.btnEnviar.setEnabled(false);
        FirebaseAuth.getInstance().sendPasswordResetEmail(correo).addOnCompleteListener(this, task -> {
            b.progress.setVisibility(View.GONE);
            b.btnEnviar.setEnabled(true);
            if (task.isSuccessful()) {
                Toast.makeText(this, R.string.msg_reset_sent, Toast.LENGTH_LONG).show();
                finish();
            } else {
                Log.e("ForgotPassword", "Error", task.getException());
                Toast.makeText(this, AuthErrors.traducir(this, task.getException()), Toast.LENGTH_LONG).show();
            }
        });
    }
}

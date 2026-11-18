package com.example.lab6_20221576;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lab6_20221576.databinding.ActivityRegisterBinding;
import com.example.lab6_20221576.util.AuthErrors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private ActivityRegisterBinding b;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        mAuth = FirebaseAuth.getInstance();

        b.btnRegistrar.setOnClickListener(v -> registrar());
        b.tvVolver.setOnClickListener(v -> finish());
    }

    private String texto(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void registrar() {
        String nombre = texto(b.etNombre);
        String correo = texto(b.etCorreo);
        String pass = b.etPassword.getText() == null ? "" : b.etPassword.getText().toString();
        String confirmar = b.etConfirmar.getText() == null ? "" : b.etConfirmar.getText().toString();

        b.tilNombre.setError(null);
        b.tilCorreo.setError(null);
        b.tilPassword.setError(null);
        b.tilConfirmar.setError(null);

        if (nombre.isEmpty()) {
            b.tilNombre.setError(getString(R.string.err_name_required));
            return;
        }
        if (correo.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            b.tilCorreo.setError(getString(correo.isEmpty()
                    ? R.string.err_email_required : R.string.err_email_invalid));
            return;
        }
        if (pass.length() < 6) {
            b.tilPassword.setError(getString(R.string.err_password_short));
            return;
        }
        if (!pass.equals(confirmar)) {
            b.tilConfirmar.setError(getString(R.string.err_password_mismatch));
            return;
        }

        setCargando(true);
        mAuth.createUserWithEmailAndPassword(correo, pass).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) {
                setCargando(false);
                Log.e(TAG, "Registro falló", task.getException());
                Toast.makeText(this, AuthErrors.traducir(this, task.getException()), Toast.LENGTH_LONG).show();
                return;
            }

            FirebaseUser user = task.getResult().getUser();
            if (user == null) {
                setCargando(false);
                return;
            }

            // 1) Correo de verificación
            user.sendEmailVerification();

            // 2) Perfil del usuario en Cloud Firestore (colección usuarios)
            Map<String, Object> perfil = new HashMap<>();
            perfil.put("nombre", nombre);
            perfil.put("correo", correo);
            perfil.put("creado", System.currentTimeMillis());
            FirebaseFirestore.getInstance().collection("usuarios").document(user.getUid()).set(perfil);

            // 3) Nombre visible y luego ir al dashboard
            UserProfileChangeRequest cambios = new UserProfileChangeRequest.Builder()
                    .setDisplayName(nombre).build();
            user.updateProfile(cambios).addOnCompleteListener(t -> {
                setCargando(false);
                Toast.makeText(this, R.string.msg_verify_sent, Toast.LENGTH_LONG).show();
                Intent i = new Intent(this, DashboardActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        });
    }

    private void setCargando(boolean cargando) {
        b.progress.setVisibility(cargando ? View.VISIBLE : View.GONE);
        b.btnRegistrar.setEnabled(!cargando);
    }
}

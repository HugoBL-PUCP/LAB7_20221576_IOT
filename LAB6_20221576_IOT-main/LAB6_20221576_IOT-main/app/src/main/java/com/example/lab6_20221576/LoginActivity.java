package com.example.lab6_20221576;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.lab6_20221576.databinding.ActivityLoginBinding;
import com.example.lab6_20221576.util.AuthErrors;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;

import java.util.Arrays;

/**
 * Pantalla de inicio: logo + login con correo, Google, Facebook,
 * y accesos a registro y recuperación de contraseña.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    /** Si es true, los usuarios de correo/contraseña deben verificar su correo para ingresar. */
    private static final boolean EXIGIR_CORREO_VERIFICADO = false;

    private ActivityLoginBinding b;
    private FirebaseAuth mAuth;
    private CredentialManager credentialManager;
    private CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        mAuth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);

        b.btnLogin.setOnClickListener(v -> loginConCorreo());
        b.btnGoogle.setOnClickListener(v -> loginConGoogle());
        configurarFacebook();
        b.btnFacebook.setOnClickListener(v -> LoginManager.getInstance()
                .logInWithReadPermissions(this, callbackManager, Arrays.asList("email", "public_profile")));

        b.tvOlvido.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));
        b.tvRegistro.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Si ya hay sesión activa, saltamos directo al dashboard.
        if (mAuth.getCurrentUser() != null) {
            irADashboard();
        }
    }

    // ===================== CORREO / CONTRASEÑA =====================
    private void loginConCorreo() {
        String correo = b.etCorreo.getText() == null ? "" : b.etCorreo.getText().toString().trim();
        String pass = b.etPassword.getText() == null ? "" : b.etPassword.getText().toString();

        b.tilCorreo.setError(null);
        b.tilPassword.setError(null);

        if (correo.isEmpty()) {
            b.tilCorreo.setError(getString(R.string.err_email_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            b.tilCorreo.setError(getString(R.string.err_email_invalid));
            return;
        }
        if (pass.isEmpty()) {
            b.tilPassword.setError(getString(R.string.err_password_required));
            return;
        }

        setCargando(true);
        mAuth.signInWithEmailAndPassword(correo, pass)
                .addOnCompleteListener(this, this::manejarResultado);
    }

    // ===================== GOOGLE (Credential Manager) =====================
    private void loginConGoogle() {
        GetGoogleIdOption opcion = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.server_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(opcion)
                .build();

        setCargando(true);
        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                .equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential google =
                                        GoogleIdTokenCredential.createFrom(credential.getData());
                                firebaseConGoogle(google.getIdToken());
                            } catch (Exception e) {
                                Log.e(TAG, "Token de Google inválido", e);
                                setCargando(false);
                                Toast.makeText(LoginActivity.this, R.string.err_google, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            setCargando(false);
                            Toast.makeText(LoginActivity.this, R.string.err_google, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        Log.e(TAG, "Google Sign-In falló", e);
                        setCargando(false);
                        if (!(e instanceof GetCredentialCancellationException)) {
                            Toast.makeText(LoginActivity.this, R.string.err_google, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void firebaseConGoogle(String idToken) {
        AuthCredential credencial = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credencial).addOnCompleteListener(this, this::manejarResultado);
    }

    // ===================== FACEBOOK =====================
    private void configurarFacebook() {
        callbackManager = CallbackManager.Factory.create();
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                firebaseConFacebook(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Toast.makeText(LoginActivity.this, R.string.msg_facebook_cancel, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull FacebookException error) {
                Log.e(TAG, "Facebook Login falló", error);
                Toast.makeText(LoginActivity.this, R.string.err_facebook, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void firebaseConFacebook(AccessToken token) {
        setCargando(true);
        AuthCredential credencial = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credencial).addOnCompleteListener(this, this::manejarResultado);
    }

    // ===================== COMUNES =====================
    private void manejarResultado(Task<AuthResult> task) {
        setCargando(false);
        if (task.isSuccessful()) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (EXIGIR_CORREO_VERIFICADO && user != null && usaCorreo(user) && !user.isEmailVerified()) {
                mAuth.signOut();
                Toast.makeText(this, R.string.msg_verify_required, Toast.LENGTH_LONG).show();
                return;
            }
            irADashboard();
        } else {
            Log.e(TAG, "Autenticación falló", task.getException());
            Toast.makeText(this, AuthErrors.traducir(this, task.getException()), Toast.LENGTH_LONG).show();
        }
    }

    private boolean usaCorreo(FirebaseUser user) {
        for (UserInfo info : user.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())) return true;
        }
        return false;
    }

    private void setCargando(boolean cargando) {
        b.progress.setVisibility(cargando ? android.view.View.VISIBLE : android.view.View.GONE);
        b.btnLogin.setEnabled(!cargando);
        b.btnGoogle.setEnabled(!cargando);
        b.btnFacebook.setEnabled(!cargando);
    }

    private void irADashboard() {
        Intent i = new Intent(this, DashboardActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}

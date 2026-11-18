package com.example.lab6_20221576;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.lab6_20221576.adapter.DispositivoAdapter;
import com.example.lab6_20221576.databinding.ActivityDashboardBinding;
import com.example.lab6_20221576.model.Dispositivo;
import com.example.lab6_20221576.util.ImageUtils;
import com.facebook.login.LoginManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla principal:
 *  - Foto de perfil del usuario  -> Cloud Storage (usuarios/{uid}/perfil.jpg) + FirebaseUser.photoUri
 *  - Lista de dispositivos       -> Cloud Firestore (lectura en tiempo real con SnapshotListener)
 *  - Eliminar                    -> borra la foto en Storage y luego el documento en Firestore
 */
public class DashboardActivity extends AppCompatActivity implements DispositivoAdapter.Listener {

    private ActivityDashboardBinding b;
    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private FirebaseStorage storage;
    private CollectionReference col;
    private ListenerRegistration registro;
    private DispositivoAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    // Selector de fotos del sistema (no requiere permisos)
    private final ActivityResultLauncher<PickVisualMediaRequest> selectorAvatar =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) subirAvatar(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDashboardBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        if (user == null) {
            irALogin();
            return;
        }
        setSupportActionBar(b.toolbar);

        storage = FirebaseStorage.getInstance();
        col = FirebaseFirestore.getInstance()
                .collection("usuarios").document(user.getUid()).collection("dispositivos");

        String nombre = !TextUtils.isEmpty(user.getDisplayName()) ? user.getDisplayName() : user.getEmail();
        b.tvSaludo.setText(getString(R.string.dashboard_hello, nombre));
        if (user.getPhotoUrl() != null) cargarAvatar(user.getPhotoUrl());

        adapter = new DispositivoAdapter(this);
        b.rvDispositivos.setLayoutManager(new LinearLayoutManager(this));
        b.rvDispositivos.setAdapter(adapter);

        b.ivAvatar.setOnClickListener(v -> selectorAvatar.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        b.fabAgregar.setOnClickListener(v -> startActivity(new Intent(this, DispositivoActivity.class)));
    }

    // ===================== READ (Firestore, tiempo real) =====================
    @Override
    protected void onStart() {
        super.onStart();
        if (col == null) return;
        registro = col.orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        Toast.makeText(this, getString(R.string.msg_db_error, error.getMessage()),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snap == null) return;
                    List<Dispositivo> lista = snap.toObjects(Dispositivo.class);
                    adapter.setItems(lista);
                    b.tvVacio.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registro != null) registro.remove();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    // ===================== FOTO DE PERFIL (Storage + Auth) =====================
    private void subirAvatar(Uri uri) {
        Toast.makeText(this, R.string.msg_uploading, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                byte[] datos = ImageUtils.comprimir(this, uri, 512, 85);
                runOnUiThread(() -> enviarAvatar(datos));
            } catch (IOException ex) {
                runOnUiThread(() -> Toast.makeText(this, R.string.msg_image_error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void enviarAvatar(byte[] datos) {
        StorageReference ref = storage.getReference("usuarios/" + user.getUid() + "/perfil.jpg");
        StorageMetadata meta = new StorageMetadata.Builder().setContentType("image/jpeg").build();

        ref.putBytes(datos, meta)
                .addOnSuccessListener(snapshot -> ref.getDownloadUrl().addOnSuccessListener(url -> {
                    UserProfileChangeRequest cambios = new UserProfileChangeRequest.Builder()
                            .setPhotoUri(url).build();
                    user.updateProfile(cambios).addOnCompleteListener(t -> {
                        cargarAvatar(url);
                        Toast.makeText(this, R.string.msg_avatar_updated, Toast.LENGTH_SHORT).show();
                    });
                }))
                .addOnFailureListener(ex -> Toast.makeText(this,
                        getString(R.string.msg_upload_error, ex.getMessage()), Toast.LENGTH_LONG).show());
    }

    private void cargarAvatar(Uri url) {
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .centerCrop()
                .into(b.ivAvatar);
    }

    // ===================== LISTA: editar / eliminar =====================
    @Override
    public void onEditar(Dispositivo dispositivo) {
        Intent i = new Intent(this, DispositivoActivity.class);
        i.putExtra(DispositivoActivity.EXTRA_DISPOSITIVO, dispositivo);
        startActivity(i);
    }

    @Override
    public void onEliminar(Dispositivo d) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_message)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dlg, which) -> eliminar(d))
                .show();
    }

    /** 1) borra la foto de Storage  2) borra el documento de Firestore. */
    private void eliminar(Dispositivo d) {
        DocumentReference doc = col.document(d.getId());
        if (TextUtils.isEmpty(d.getFotoPath())) {
            borrarDocumento(doc);
            return;
        }
        storage.getReference(d.getFotoPath()).delete()
                .addOnSuccessListener(u -> borrarDocumento(doc))
                .addOnFailureListener(ex -> {
                    // Si el archivo ya no existe en Storage, igual limpiamos el documento
                    if (ex instanceof StorageException
                            && ((StorageException) ex).getErrorCode() == StorageException.ERROR_OBJECT_NOT_FOUND) {
                        borrarDocumento(doc);
                    } else {
                        Toast.makeText(this, getString(R.string.msg_db_error, ex.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void borrarDocumento(DocumentReference doc) {
        doc.delete()
                .addOnSuccessListener(u -> Toast.makeText(this, R.string.msg_deleted, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(ex -> Toast.makeText(this,
                        getString(R.string.msg_db_error, ex.getMessage()), Toast.LENGTH_LONG).show());
    }

    // ===================== MENÚ =====================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_cerrar_sesion) {
            mAuth.signOut();
            LoginManager.getInstance().logOut();
            irALogin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void irALogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}

package com.example.lab6_20221576;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.lab6_20221576.databinding.ActivityDispositivoBinding;
import com.example.lab6_20221576.model.Dispositivo;
import com.example.lab6_20221576.util.ImageUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Crear / editar un dispositivo.
 * Flujo al guardar:
 *   1) (si hay foto nueva) comprimir -> subir a Cloud Storage con barra de progreso
 *   2) pedir la URL de descarga
 *   3) guardar/actualizar el documento en Cloud Firestore con la URL y la ruta
 */
public class DispositivoActivity extends AppCompatActivity {

    public static final String EXTRA_DISPOSITIVO = "dispositivo";
    private static final String KEY_IMAGEN = "imagenNueva";
    private static final String KEY_CAMARA = "camaraUri";

    private ActivityDispositivoBinding b;
    private FirebaseStorage storage;
    private CollectionReference col;
    private String uid;

    private Dispositivo existente;   // null = modo "nuevo"
    private Uri imagenNueva;         // foto elegida, aún no subida
    private Uri camaraUri;           // archivo donde la cámara guarda la foto
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<PickVisualMediaRequest> galeria =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) seleccionar(uri);
            });

    private final ActivityResultLauncher<Uri> camara =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && camaraUri != null) seleccionar(camaraUri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityDispositivoBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();
        storage = FirebaseStorage.getInstance();
        col = FirebaseFirestore.getInstance()
                .collection("usuarios").document(uid).collection("dispositivos");

        b.toolbar.setNavigationOnClickListener(v -> finish());
        b.spTipo.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Dispositivo.TIPOS));

        existente = getIntent().getSerializableExtra(EXTRA_DISPOSITIVO, Dispositivo.class);
        if (existente != null) {
            b.toolbar.setTitle(R.string.form_edit_title);
            b.etNombre.setText(existente.getNombre());
            b.etUbicacion.setText(existente.getUbicacion());
            b.etDescripcion.setText(existente.getDescripcion());
            b.spTipo.setSelection(Math.max(0, Arrays.asList(Dispositivo.TIPOS).indexOf(existente.getTipo())));
            if (!TextUtils.isEmpty(existente.getFotoUrl())) {
                Glide.with(this).load(existente.getFotoUrl())
                        .placeholder(R.drawable.ic_placeholder).centerCrop().into(b.ivFoto);
            }
        } else {
            b.toolbar.setTitle(R.string.form_new_title);
        }

        // Restaurar estado si la pantalla se recreó (p. ej. al rotar o volver de la cámara)
        if (savedInstanceState != null) {
            camaraUri = savedInstanceState.getParcelable(KEY_CAMARA, Uri.class);
            Uri guardada = savedInstanceState.getParcelable(KEY_IMAGEN, Uri.class);
            if (guardada != null) seleccionar(guardada);
        }

        b.btnGaleria.setOnClickListener(v -> galeria.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        b.btnCamara.setOnClickListener(v -> abrirCamara());
        b.btnGuardar.setOnClickListener(v -> guardar());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CAMARA, camaraUri);
        outState.putParcelable(KEY_IMAGEN, imagenNueva);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    // ===================== ELEGIR IMAGEN =====================
    private void abrirCamara() {
        try {
            camaraUri = ImageUtils.nuevaUriCamara(this);
            camara.launch(camaraUri);
        } catch (IOException e) {
            Toast.makeText(this, R.string.msg_camera_error, Toast.LENGTH_LONG).show();
        }
    }

    private void seleccionar(Uri uri) {
        imagenNueva = uri;
        Glide.with(this).load(uri).centerCrop().into(b.ivFoto);
    }

    // ===================== GUARDAR =====================
    private void guardar() {
        String nombre = texto(b.etNombre);
        String ubicacion = texto(b.etUbicacion);
        String descripcion = texto(b.etDescripcion);
        String tipo = (String) b.spTipo.getSelectedItem();

        b.tilNombre.setError(null);
        b.tilUbicacion.setError(null);
        if (nombre.isEmpty()) {
            b.tilNombre.setError(getString(R.string.err_device_name_required));
            return;
        }
        if (ubicacion.isEmpty()) {
            b.tilUbicacion.setError(getString(R.string.err_location_required));
            return;
        }

        // Si es nuevo: col.document() genera el id (lo necesitamos antes para nombrar el archivo)
        DocumentReference doc = existente == null ? col.document() : col.document(existente.getId());
        String urlActual = existente == null ? "" : existente.getFotoUrl();
        String pathActual = existente == null ? "" : existente.getFotoPath();

        setCargando(true);
        if (imagenNueva == null) {
            persistir(doc, nombre, ubicacion, tipo, descripcion, urlActual, pathActual);
            return;
        }

        // Comprimir en un hilo aparte y luego subir
        final Uri origen = imagenNueva;
        io.execute(() -> {
            try {
                byte[] datos = ImageUtils.comprimir(this, origen, 1280, 85);
                runOnUiThread(() -> subir(doc, datos, nombre, ubicacion, tipo, descripcion));
            } catch (IOException ex) {
                runOnUiThread(() -> {
                    setCargando(false);
                    Toast.makeText(this, R.string.msg_image_error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ===================== CLOUD STORAGE =====================
    private void subir(DocumentReference doc, byte[] datos, String nombre, String ubicacion,
                       String tipo, String descripcion) {
        // Ruta:  usuarios/{uid}/dispositivos/{idDocumento}.jpg
        String path = "usuarios/" + uid + "/dispositivos/" + doc.getId() + ".jpg";
        StorageReference ref = storage.getReference(path);
        StorageMetadata meta = new StorageMetadata.Builder().setContentType("image/jpeg").build();

        b.layoutProgreso.setVisibility(View.VISIBLE);
        b.progressSubida.setProgress(0);

        ref.putBytes(datos, meta)
                .addOnProgressListener(snap -> {
                    int pct = (int) (100.0 * snap.getBytesTransferred() / snap.getTotalByteCount());
                    b.progressSubida.setProgress(pct);
                    b.tvProgreso.setText(getString(R.string.msg_uploading) + " " + pct + " %");
                })
                .addOnSuccessListener(snap -> ref.getDownloadUrl()
                        .addOnSuccessListener(url -> persistir(doc, nombre, ubicacion, tipo, descripcion,
                                url.toString(), path))
                        .addOnFailureListener(this::errorSubida))
                .addOnFailureListener(this::errorSubida);
    }

    private void errorSubida(Exception ex) {
        setCargando(false);
        b.layoutProgreso.setVisibility(View.GONE);
        Toast.makeText(this, getString(R.string.msg_upload_error, ex.getMessage()), Toast.LENGTH_LONG).show();
    }

    // ===================== CLOUD FIRESTORE =====================
    private void persistir(DocumentReference doc, String nombre, String ubicacion, String tipo,
                           String descripcion, String fotoUrl, String fotoPath) {
        if (existente == null) {
            // CREATE
            Dispositivo nuevo = new Dispositivo(nombre, ubicacion, tipo, descripcion,
                    fotoUrl, fotoPath, System.currentTimeMillis());
            doc.set(nuevo)
                    .addOnSuccessListener(u -> terminar(R.string.msg_saved))
                    .addOnFailureListener(this::errorBD);
        } else {
            // UPDATE (solo los campos que cambian)
            Map<String, Object> cambios = new HashMap<>();
            cambios.put("nombre", nombre);
            cambios.put("ubicacion", ubicacion);
            cambios.put("tipo", tipo);
            cambios.put("descripcion", descripcion);
            cambios.put("fotoUrl", fotoUrl);
            cambios.put("fotoPath", fotoPath);
            doc.update(cambios)
                    .addOnSuccessListener(u -> terminar(R.string.msg_updated))
                    .addOnFailureListener(this::errorBD);
        }
    }

    private void terminar(int mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void errorBD(Exception ex) {
        setCargando(false);
        Toast.makeText(this, getString(R.string.msg_db_error, ex.getMessage()), Toast.LENGTH_LONG).show();
    }

    // ===================== AUXILIARES =====================
    private String texto(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void setCargando(boolean cargando) {
        b.btnGuardar.setEnabled(!cargando);
        b.btnGaleria.setEnabled(!cargando);
        b.btnCamara.setEnabled(!cargando);
    }
}

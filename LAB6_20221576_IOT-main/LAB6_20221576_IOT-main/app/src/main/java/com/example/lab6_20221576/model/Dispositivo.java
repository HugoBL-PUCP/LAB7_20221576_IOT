package com.example.lab6_20221576.model;

import com.google.firebase.firestore.DocumentId;

import java.io.Serializable;

/**
 * Documento de Cloud Firestore:  usuarios / {uid} / dispositivos / {id}
 *
 * - El archivo de la foto vive en Cloud Storage; aquí solo guardamos:
 *     fotoPath -> ruta en Storage (sirve para borrarla/reemplazarla)
 *     fotoUrl  -> URL de descarga (sirve para mostrarla con Glide)
 * - @DocumentId: Firestore rellena "id" con el id del documento (no se guarda como campo).
 * - Serializable: para pasar el objeto entre pantallas con un Intent.
 */
public class Dispositivo implements Serializable {

    public static final String[] TIPOS = {"Sensor", "Actuador", "Gateway", "Cámara"};

    @DocumentId
    private String id;
    private String nombre;
    private String ubicacion;
    private String tipo;
    private String descripcion;
    private String fotoUrl;
    private String fotoPath;
    private long timestamp;

    public Dispositivo() { }

    public Dispositivo(String nombre, String ubicacion, String tipo, String descripcion,
                       String fotoUrl, String fotoPath, long timestamp) {
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.fotoUrl = fotoUrl;
        this.fotoPath = fotoPath;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getFotoPath() { return fotoPath; }
    public void setFotoPath(String fotoPath) { this.fotoPath = fotoPath; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

package com.example.lab6_20221576.model;

import com.google.firebase.database.Exclude;

/**
 * Modelo de una lectura de sensor IoT.
 * Requisitos de Firebase: constructor vacío público + getters/setters.
 * El "id" NO se guarda dentro del nodo (es la clave del nodo), por eso @Exclude.
 */
public class Lectura {
    private String id;
    private String dispositivo;
    private String tipo;
    private double valor;
    private String unidad;
    private long timestamp;

    public Lectura() { }

    public Lectura(String dispositivo, String tipo, double valor, String unidad, long timestamp) {
        this.dispositivo = dispositivo;
        this.tipo = tipo;
        this.valor = valor;
        this.unidad = unidad;
        this.timestamp = timestamp;
    }

    @Exclude
    public String getId() { return id; }
    @Exclude
    public void setId(String id) { this.id = id; }

    public String getDispositivo() { return dispositivo; }
    public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }

    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

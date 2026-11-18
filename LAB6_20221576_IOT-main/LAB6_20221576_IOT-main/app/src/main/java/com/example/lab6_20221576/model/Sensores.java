package com.example.lab6_20221576.model;

/** Catálogo de tipos de sensor y su unidad de medida. */
public final class Sensores {
    private Sensores() { }

    public static final String[] TIPOS = {"Temperatura", "Humedad", "Luminosidad", "CO2"};
    public static final String[] UNIDADES = {"°C", "%", "lx", "ppm"};

    public static String unidadDe(String tipo) {
        for (int i = 0; i < TIPOS.length; i++) {
            if (TIPOS[i].equals(tipo)) return UNIDADES[i];
        }
        return "";
    }
}

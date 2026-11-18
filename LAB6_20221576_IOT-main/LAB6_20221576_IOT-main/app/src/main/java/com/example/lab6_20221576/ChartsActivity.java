package com.example.lab6_20221576;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lab6_20221576.databinding.ActivityChartsBinding;
import com.example.lab6_20221576.model.Lectura;
import com.example.lab6_20221576.model.Sensores;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Gráficos construidos a partir de los datos guardados en Firebase.
 *  - Gráfico 1 (líneas): evolución de las lecturas  -> filtros: tipo de sensor + dispositivo
 *  - Gráfico 2 (barras): promedio por dispositivo   -> filtro: tipo de sensor
 */
public class ChartsActivity extends AppCompatActivity {

    private ActivityChartsBinding b;
    private DatabaseReference ref;
    private ValueEventListener listener;
    private final List<Lectura> todas = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityChartsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        ref = FirebaseFirestore.getInstance();

        // Spinners de filtro
        b.spTipo.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Sensores.TIPOS));
        List<String> soloTodos = new ArrayList<>();
        soloTodos.add(getString(R.string.filter_all));
        b.spDispositivo.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, soloTodos));

        AdapterView.OnItemSelectedListener filtro = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                aplicarFiltros();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        b.spTipo.setOnItemSelectedListener(filtro);
        b.spDispositivo.setOnItemSelectedListener(filtro);

        configurarGraficos();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ref == null) return;
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                todas.clear();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    Lectura l = hijo.getValue(Lectura.class);
                    if (l != null) {
                        l.setId(hijo.getKey());
                        todas.add(l);
                    }
                }
                actualizarSpinnerDispositivos();
                aplicarFiltros();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        ref.addSnapshotListener(listener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ref != null && listener != null) ref.removeEventListener(listener);
    }

    // ===================== FILTROS =====================
    /** Llena el spinner de dispositivos con los que existen en los datos. */
    private void actualizarSpinnerDispositivos() {
        String seleccionado = (String) b.spDispositivo.getSelectedItem();
        TreeSet<String> dispositivos = new TreeSet<>();
        for (Lectura l : todas) {
            if (l.getDispositivo() != null) dispositivos.add(l.getDispositivo());
        }
        List<String> opciones = new ArrayList<>();
        opciones.add(getString(R.string.filter_all));
        opciones.addAll(dispositivos);

        b.spDispositivo.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, opciones));
        int idx = seleccionado == null ? 0 : opciones.indexOf(seleccionado);
        b.spDispositivo.setSelection(Math.max(idx, 0));
    }

    private void aplicarFiltros() {
        String tipo = (String) b.spTipo.getSelectedItem();
        String dispositivo = (String) b.spDispositivo.getSelectedItem();
        boolean todosLosDispositivos = dispositivo == null
                || dispositivo.equals(getString(R.string.filter_all));

        List<Lectura> porTipo = new ArrayList<>();
        List<Lectura> porTipoYDispositivo = new ArrayList<>();
        for (Lectura l : todas) {
            if (tipo != null && tipo.equals(l.getTipo())) {
                porTipo.add(l);
                if (todosLosDispositivos || dispositivo.equals(l.getDispositivo())) {
                    porTipoYDispositivo.add(l);
                }
            }
        }
        Collections.sort(porTipoYDispositivo, (x, y) -> Long.compare(x.getTimestamp(), y.getTimestamp()));

        dibujarLineas(porTipoYDispositivo, tipo);
        dibujarBarras(porTipo, tipo);
    }

    // ===================== GRÁFICOS =====================
    private void configurarGraficos() {
        b.chartLinea.getDescription().setEnabled(false);
        b.chartLinea.getAxisRight().setEnabled(false);
        b.chartLinea.setNoDataText(getString(R.string.chart_no_data));
        XAxis xl = b.chartLinea.getXAxis();
        xl.setPosition(XAxis.XAxisPosition.BOTTOM);
        xl.setGranularity(1f);
        xl.setLabelRotationAngle(-45f);

        b.chartBarras.getDescription().setEnabled(false);
        b.chartBarras.getAxisRight().setEnabled(false);
        b.chartBarras.setNoDataText(getString(R.string.chart_no_data));
        XAxis xb = b.chartBarras.getXAxis();
        xb.setPosition(XAxis.XAxisPosition.BOTTOM);
        xb.setGranularity(1f);
        xb.setDrawGridLines(false);
    }

    private void dibujarLineas(List<Lectura> datos, String tipo) {
        if (datos.isEmpty()) {
            b.chartLinea.clear();
            b.chartLinea.invalidate();
            return;
        }
        List<Entry> entradas = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        for (int i = 0; i < datos.size(); i++) {
            Lectura l = datos.get(i);
            entradas.add(new Entry(i, (float) l.getValor()));
            etiquetas.add(fmt.format(new Date(l.getTimestamp())));
        }

        LineDataSet set = new LineDataSet(entradas, tipo + " (" + Sensores.unidadDe(tipo) + ")");
        set.setColor(getColor(R.color.primary));
        set.setCircleColor(getColor(R.color.primary));
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setValueTextSize(10f);

        b.chartLinea.getXAxis().setValueFormatter(new IndexAxisValueFormatter(etiquetas));
        b.chartLinea.getXAxis().setLabelCount(Math.min(etiquetas.size(), 6), false);
        b.chartLinea.setData(new LineData(set));
        b.chartLinea.animateX(500);
        b.chartLinea.invalidate();
    }

    private void dibujarBarras(List<Lectura> datos, String tipo) {
        if (datos.isEmpty()) {
            b.chartBarras.clear();
            b.chartBarras.invalidate();
            return;
        }
        // dispositivo -> [suma, cantidad]
        Map<String, double[]> acumulado = new TreeMap<>();
        for (Lectura l : datos) {
            String disp = l.getDispositivo() == null ? "?" : l.getDispositivo();
            double[] a = acumulado.get(disp);
            if (a == null) {
                a = new double[2];
                acumulado.put(disp, a);
            }
            a[0] += l.getValor();
            a[1] += 1;
        }

        List<BarEntry> entradas = new ArrayList<>();
        List<String> etiquetas = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, double[]> e : acumulado.entrySet()) {
            entradas.add(new BarEntry(i++, (float) (e.getValue()[0] / e.getValue()[1])));
            etiquetas.add(e.getKey());
        }

        BarDataSet set = new BarDataSet(entradas, "Promedio de " + tipo + " (" + Sensores.unidadDe(tipo) + ")");
        set.setColor(getColor(R.color.secondary));
        set.setValueTextSize(11f);

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);

        b.chartBarras.getXAxis().setValueFormatter(new IndexAxisValueFormatter(etiquetas));
        b.chartBarras.getXAxis().setLabelCount(etiquetas.size());
        b.chartBarras.setData(data);
        b.chartBarras.setFitBars(true);
        b.chartBarras.animateY(500);
        b.chartBarras.invalidate();
    }
}

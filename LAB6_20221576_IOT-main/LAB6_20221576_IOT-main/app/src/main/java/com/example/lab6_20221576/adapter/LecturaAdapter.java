package com.example.lab6_20221576.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lab6_20221576.databinding.ItemLecturaBinding;
import com.example.lab6_20221576.model.Lectura;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LecturaAdapter extends RecyclerView.Adapter<LecturaAdapter.VH> {

    public interface Listener {
        void onEditar(Lectura lectura);
        void onEliminar(Lectura lectura);
    }

    private final List<Lectura> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public LecturaAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Lectura> nuevos) {
        items.clear();
        items.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLecturaBinding b = ItemLecturaBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Lectura l = items.get(position);
        holder.b.tvDispositivo.setText(l.getDispositivo());
        holder.b.tvDato.setText(String.format(Locale.getDefault(), "%s: %.1f %s",
                l.getTipo(), l.getValor(), l.getUnidad()));
        holder.b.tvFecha.setText(fmt.format(new Date(l.getTimestamp())));
        holder.b.getRoot().setOnClickListener(v -> listener.onEditar(l));
        holder.b.btnEliminar.setOnClickListener(v -> listener.onEliminar(l));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemLecturaBinding b;

        VH(ItemLecturaBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}

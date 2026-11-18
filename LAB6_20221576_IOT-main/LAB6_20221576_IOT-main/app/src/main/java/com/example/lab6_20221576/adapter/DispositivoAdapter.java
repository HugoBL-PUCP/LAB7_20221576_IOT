package com.example.lab6_20221576.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.lab6_20221576.R;
import com.example.lab6_20221576.databinding.ItemDispositivoBinding;
import com.example.lab6_20221576.model.Dispositivo;

import java.util.ArrayList;
import java.util.List;

public class DispositivoAdapter extends RecyclerView.Adapter<DispositivoAdapter.VH> {

    public interface Listener {
        void onEditar(Dispositivo dispositivo);
        void onEliminar(Dispositivo dispositivo);
    }

    private final List<Dispositivo> items = new ArrayList<>();
    private final Listener listener;

    public DispositivoAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Dispositivo> nuevos) {
        items.clear();
        items.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemDispositivoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Dispositivo d = items.get(position);
        h.b.tvNombre.setText(d.getNombre());
        h.b.tvTipo.setText(d.getTipo());
        h.b.tvUbicacion.setText(d.getUbicacion());

        // Glide descarga la imagen desde la URL de Cloud Storage y la guarda en caché
        if (!TextUtils.isEmpty(d.getFotoUrl())) {
            Glide.with(h.b.ivFoto)
                    .load(d.getFotoUrl())
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_placeholder)
                    .centerCrop()
                    .into(h.b.ivFoto);
        } else {
            Glide.with(h.b.ivFoto).clear(h.b.ivFoto);
            h.b.ivFoto.setImageResource(R.drawable.ic_placeholder);
        }

        h.b.getRoot().setOnClickListener(v -> listener.onEditar(d));
        h.b.btnEliminar.setOnClickListener(v -> listener.onEliminar(d));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemDispositivoBinding b;

        VH(ItemDispositivoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}

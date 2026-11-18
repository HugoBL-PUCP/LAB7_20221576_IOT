package com.example.lab6_20221576.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public final class ImageUtils {
    private ImageUtils() { }

    /**
     * Lee la imagen, la reduce para que su lado mayor sea <= maxLado y la comprime a JPEG.
     * Así evitamos subir fotos de 8 MB (más rápido, más barato y cumple la regla de tamaño máximo).
     * ImageDecoder respeta la orientación EXIF automáticamente.
     * Llamar desde un hilo secundario.
     */
    public static byte[] comprimir(Context ctx, Uri uri, int maxLado, int calidad) throws IOException {
        ImageDecoder.Source fuente = ImageDecoder.createSource(ctx.getContentResolver(), uri);
        Bitmap bmp = ImageDecoder.decodeBitmap(fuente, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            float escala = Math.min(1f, (float) maxLado / Math.max(w, h));
            decoder.setTargetSize(Math.max(1, Math.round(w * escala)), Math.max(1, Math.round(h * escala)));
        });
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, calidad, salida);
        return salida.toByteArray();
    }

    /** Crea un archivo temporal en caché y devuelve su Uri (vía FileProvider) para la cámara. */
    public static Uri nuevaUriCamara(Context ctx) throws IOException {
        File dir = new File(ctx.getCacheDir(), "images");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File archivo = File.createTempFile("foto_", ".jpg", dir);
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", archivo);
    }
}

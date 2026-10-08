package soka;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Loader untuk dataset GoCJ (Google Cloud Jobs) + generator dataset sintetis.
 *
 * CARA PAKAI DATASET ASLI:
 * 1. Taruh file GoCJ_Dataset_N.txt di src/main/resources/Dataset_GoCJ/
 *    (isinya satu angka panjang task dalam MI per baris).
 * 2. Panggil GoCJLoader.loadFromResource("Dataset_GoCJ/GoCJ_Dataset_N.txt").
 *
 * DATASET SINTETIS:
 * generateSynthetic(jumlahTask, seed, proporsi) menghasilkan dataset dengan
 * proporsi kategori yang ditentukan sendiri. Jumlah task per kategori EXACT
 * (bukan acak), jadi proporsi selalu sama di setiap seed; yang berbeda antar
 * seed adalah nilai MI di dalam kategori dan urutan task.
 */
public class GoCJLoader {

    /** Rentang MI per kategori: Small, Medium, Large, Extra Large, Huge (sesuai README). */
    private static final long[][] KATEGORI = {
        {1_000, 10_000},      // Small
        {10_000, 50_000},     // Medium
        {50_000, 100_000},    // Large
        {100_000, 200_000},   // Extra Large
        {200_000, 300_000}    // Huge
    };

    /** Baca panjang cloudlet (dalam Million Instructions) dari file teks, satu angka per baris. */
    public static List<Long> loadFromFile(String path) throws Exception {
        List<Long> lengths = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lengths.add(Long.parseLong(line));
                }
            }
        }
        return lengths;
    }

    /** Membaca dataset yang disimpan di src/main/resources. */
    public static List<Long> loadFromResource(String resourcePath) throws Exception {
        InputStream stream = GoCJLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (stream == null) throw new IllegalArgumentException("Dataset tidak ditemukan: " + resourcePath);
        List<Long> lengths = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lengths.add(Long.parseLong(line));
            }
        }
        return lengths;
    }

    /**
     * Generator sintetis dengan proporsi kategori yang bisa diatur.
     *
     * @param jumlahTask total task
     * @param seed       seed RNG (seed sama -> dataset identik)
     * @param proporsi   5 angka (Small, Medium, Large, XL, Huge) yang jumlahnya 1.0
     */
    public static List<Long> generateSynthetic(int jumlahTask, long seed, double[] proporsi) {
        if (proporsi.length != KATEGORI.length) {
            throw new IllegalArgumentException("Proporsi harus berisi " + KATEGORI.length + " angka");
        }
        double total = 0;
        for (double p : proporsi) total += p;
        if (Math.abs(total - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Jumlah proporsi harus 1.0, sekarang " + total);
        }

        // Jumlah task per kategori (dibulatkan ke bawah); sisa pembulatan masuk ke kategori terbesar.
        int[] jumlah = new int[KATEGORI.length];
        int terpakai = 0;
        int idxTerbesar = 0;
        for (int i = 0; i < KATEGORI.length; i++) {
            jumlah[i] = (int) Math.floor(jumlahTask * proporsi[i] + 1e-9);
            terpakai += jumlah[i];
            if (proporsi[i] > proporsi[idxTerbesar]) idxTerbesar = i;
        }
        jumlah[idxTerbesar] += jumlahTask - terpakai;

        Random rnd = new Random(seed);
        List<Long> lengths = new ArrayList<>(jumlahTask);
        for (int i = 0; i < KATEGORI.length; i++) {
            long min = KATEGORI[i][0];
            long max = KATEGORI[i][1];
            for (int j = 0; j < jumlah[i]; j++) {
                lengths.add(min + (long) (rnd.nextDouble() * (max - min)));
            }
        }
        // Acak urutan supaya tidak terurut per kategori (penting untuk Round Robin).
        Collections.shuffle(lengths, rnd);
        return lengths;
    }

    /**
     * Generator lama (kategori dipilih seragam 20% tiap kategori). Dipertahankan
     * supaya kode lain yang memanggilnya tidak rusak.
     */
    public static List<Long> generateSyntheticGoCJLike(int jumlahTask, long seed) {
        Random rnd = new Random(seed);
        List<Long> lengths = new ArrayList<>();
        for (int i = 0; i < jumlahTask; i++) {
            int kat = rnd.nextInt(KATEGORI.length);
            long min = KATEGORI[kat][0];
            long max = KATEGORI[kat][1];
            long length = min + (long) (rnd.nextDouble() * (max - min));
            lengths.add(length);
        }
        return lengths;
    }
}
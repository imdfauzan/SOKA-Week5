package soka;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loader untuk dataset GoCJ (Google Cloud Jobs).
 *
 * CARA PAKAI DATASET ASLI:
 * 1. Download file GoCJ_Dataset_500.txt / .csv dari Mendeley Data
 *    (cari "GoCJ Google Cloud Jobs Dataset" di Google / Mendeley).
 * 2. Taruh file itu di folder src/main/resources/, misal "GoCJ_Dataset_500.txt"
 *    (isinya satu angka panjang task dalam MI per baris).
 * 3. Panggil GoCJLoader.loadFromFile("src/main/resources/GoCJ_Dataset_500.txt").
 *
 * Jika file belum ada / belum sempat download, dipakai generator sintetis
 * di bawah ini yang MENIRU pola GoCJ (job length bervariasi dari kecil
 * sampai sangat besar) supaya kamu tetap bisa jalankan & demo sekarang.
 * Sebelum submit final project, GANTI ke dataset asli.
 */
public class GoCJLoader {

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

    /**
     * Generator sintetis meniru distribusi GoCJ: campuran task
     * Small, Medium, Large, Extra Large, dan Huge (dalam MI).
     * Dipakai sebagai fallback / untuk uji coba cepat sebelum dataset asli didapat.
     */
    public static List<Long> generateSyntheticGoCJLike(int jumlahTask, long seed) {
        Random rnd = new Random(seed);
        List<Long> lengths = new ArrayList<>();
        // rentang MI per kategori -- nilai perkiraan, sesuaikan jika sudah pegang dataset asli
        long[][] kategori = {
            {1_000, 10_000},      // Small
            {10_000, 50_000},     // Medium
            {50_000, 100_000},    // Large
            {100_000, 200_000},   // Extra Large
            {200_000, 300_000}    // Huge
        };
        for (int i = 0; i < jumlahTask; i++) {
            int kat = rnd.nextInt(kategori.length);
            long min = kategori[kat][0];
            long max = kategori[kat][1];
            long length = min + (long) (rnd.nextDouble() * (max - min));
            lengths.add(length);
        }
        return lengths;
    }
}

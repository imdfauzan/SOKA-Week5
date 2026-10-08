package soka;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menghitung metrik-metrik yang disebut di slide B4:
 * Makespan, Resource Utilization (RU), Degree of Imbalance (DI),
 * Throughput, dan Average Response Time.
 */
public class MetricsCalculator {
    public record Hasil(double makespan, double di, double ru,
                    double throughput, double art) { }

    public static Hasil hitung(List<Cloudlet> cloudletSelesai, List<Vm> semuaVm) {
        double makespan = cloudletSelesai.stream()
                .mapToDouble(Cloudlet::getFinishTime).max().orElse(0);

        Map<Vm, Double> bebanPerVm = new HashMap<>();
        semuaVm.forEach(vm -> bebanPerVm.put(vm, 0.0));
        for (Cloudlet cl : cloudletSelesai) {
            bebanPerVm.merge(cl.getVm(), cl.getFinishTime() - cl.getStartTime(), Double::sum);
        }
        double bMax = bebanPerVm.values().stream().mapToDouble(v -> v).max().orElse(0);
        double bMin = bebanPerVm.values().stream().mapToDouble(v -> v).min().orElse(0);
        double bAvg = bebanPerVm.values().stream().mapToDouble(v -> v).average().orElse(1);
        double di = (bAvg == 0) ? 0 : (bMax - bMin) / bAvg;

        double throughput = (makespan == 0) ? 0 : cloudletSelesai.size() / makespan;
        double art = cloudletSelesai.stream()
                .mapToDouble(cl -> cl.getFinishTime() - cl.getSubmissionDelay())
                .average().orElse(0);

        long totalPes = semuaVm.stream().mapToLong(Vm::getPesNumber).sum();
        double totalCpu = bebanPerVm.values().stream().mapToDouble(Double::doubleValue).sum();
        double ru = (makespan == 0 || totalPes == 0) ? 0 : totalCpu / (totalPes * makespan) * 100;

        return new Hasil(makespan, di, ru, throughput, art);
    }

    public static void cetakRingkasan(String namaAlgoritma, List<Cloudlet> cloudletSelesai, List<Vm> semuaVm) {
        if (cloudletSelesai.isEmpty()) {
            System.out.println("Tidak ada cloudlet yang selesai untuk " + namaAlgoritma);
            return;
        }

        // 1. MAKESPAN = waktu selesai TERLAMA di antara semua cloudlet
        double makespan = cloudletSelesai.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0);

        // 2. Beban tiap VM (total waktu eksekusi cloudlet yang dijalankan VM itu)
        //    -> dipakai untuk Degree of Imbalance (DI)
        Map<Vm, Double> bebanPerVm = new HashMap<>();
        semuaVm.forEach(vm -> bebanPerVm.put(vm, 0.0));
        for (Cloudlet cl : cloudletSelesai) {
            Vm vm = cl.getVm();
            // CloudSim Plus 8.5.4 menyediakan getStartTime(), bukan
            // getActualCpuTime()/getExecStartTime(). Selisih waktu mulai dan
            // selesai adalah durasi eksekusi cloudlet yang dipakai untuk
            // menghitung total beban pada VM.
            double execTime = cl.getFinishTime() - cl.getStartTime();
            bebanPerVm.merge(vm, execTime, Double::sum);
        }
        double bebanMax = bebanPerVm.values().stream().mapToDouble(v -> v).max().orElse(0);
        double bebanMin = bebanPerVm.values().stream().mapToDouble(v -> v).min().orElse(0);
        double bebanAvg = bebanPerVm.values().stream().mapToDouble(v -> v).average().orElse(1);
        double degreeOfImbalance = (bebanAvg == 0) ? 0 : (bebanMax - bebanMin) / bebanAvg;

        // 3. THROUGHPUT = jumlah cloudlet selesai / makespan
        double throughput = (makespan == 0) ? 0 : cloudletSelesai.size() / makespan;

        // 4. AVERAGE RESPONSE TIME = rata-rata (waktu selesai - waktu submit)
        double avgResponseTime = cloudletSelesai.stream()
                .mapToDouble(cl -> cl.getFinishTime() - cl.getSubmissionDelay())
                .average()
                .orElse(0);

        // 5. RESOURCE UTILIZATION (RU)
        // Setiap VM dapat memiliki lebih dari satu PE. Karena itu, penyebut
        // harus memakai total kapasitas PE selama makespan agar RU tidak
        // melebihi 100% hanya karena VM menjalankan beberapa PE paralel.
        long totalPes = semuaVm.stream().mapToLong(Vm::getPesNumber).sum();
        double totalWaktuCpu = bebanPerVm.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        double resourceUtilization = (makespan == 0 || totalPes == 0)
                ? 0
                : totalWaktuCpu / (totalPes * makespan) * 100;

        System.out.println("\n================ HASIL: " + namaAlgoritma + " ================");
        System.out.printf("Makespan               : %.2f detik%n", makespan);
        System.out.printf("Degree of Imbalance(DI): %.4f%n", degreeOfImbalance);
        System.out.printf("Resource Utilization   : %.2f%%\n", resourceUtilization);
        System.out.printf("Throughput             : %.4f task/detik%n", throughput);
        System.out.printf("Avg. Response Time     : %.2f detik%n", avgResponseTime);
        long vmAktif = bebanPerVm.values().stream().filter(beban -> beban > 0).count();
        System.out.println("Jumlah VM aktif dipakai: " + vmAktif);
        System.out.println("=================================================\n");
    }
}

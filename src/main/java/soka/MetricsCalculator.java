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

    public static void cetakRingkasan(String namaAlgoritma, List<Cloudlet> cloudletSelesai) {
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

        // 5. RESOURCE UTILIZATION (RU) sederhana = rata-rata (beban VM / makespan)
        double resourceUtilization = bebanPerVm.values().stream()
                .mapToDouble(beban -> makespan == 0 ? 0 : beban / makespan)
                .average()
                .orElse(0) * 100;

        System.out.println("\n================ HASIL: " + namaAlgoritma + " ================");
        System.out.printf("Makespan               : %.2f detik%n", makespan);
        System.out.printf("Degree of Imbalance(DI): %.4f%n", degreeOfImbalance);
        System.out.printf("Resource Utilization   : %.2f%%\n", resourceUtilization);
        System.out.printf("Throughput             : %.4f task/detik%n", throughput);
        System.out.printf("Avg. Response Time     : %.2f detik%n", avgResponseTime);
        System.out.println("Jumlah VM aktif dipakai: " + bebanPerVm.size());
        System.out.println("=================================================\n");
    }
}

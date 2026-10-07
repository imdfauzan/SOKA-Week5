package soka;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import soka.scheduler.RoundRobinScheduler;
import soka.scheduler.SufferageScheduler;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Runner: Sufferage sebagai algoritma utama dan Round Robin sebagai baseline.
 *
 * Mode default      : java -jar target/soka-simulasi.jar               (GoCJ 500 & 1000, cetak detail)
 * Mode eksperimen   : java -jar target/soka-simulasi.jar --eksperimen  (GoCJ 100..1000, 3 run, tulis hasil.csv)
 */
public class Kelompok7Simulation {
    private record Algoritma(String nama, BiFunction<List<Cloudlet>, List<Vm>, Map<Cloudlet, Vm>> scheduler) { }

    /** Metrik hasil satu run (dihitung di sini supaya MetricsCalculator tidak perlu diubah). */
    private record Hasil(double makespan, double di, double ru, double throughput, double art) { }

    private record Keluaran(Hasil hasil, double schedMs) { }

    public static void main(String[] args) {
        List<Algoritma> algoritma = List.of(
                new Algoritma("Sufferage (LBMM)", SufferageScheduler::schedule),
                new Algoritma("Round Robin (baseline)", RoundRobinScheduler::schedule)
        );
        if (args.length > 0 && args[0].equals("--eksperimen")) {
            eksperimenGoCJ(algoritma);
            return;
        }
        for (String dataset : List.of("GoCJ_Dataset_500.txt", "GoCJ_Dataset_1000.txt")) {
            System.out.println("\n================ DATASET: " + dataset + " ================");
            for (Algoritma a : algoritma) jalankan(a, dataset, true);
        }
    }

    private static void eksperimenGoCJ(List<Algoritma> algoritma) {
        final int RUN = 3;
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("hasil.csv")))) {
            w.println("dataset_type,size,algorithm,run,makespan,di,ru,throughput,art,sched_ms");
            for (int size = 100; size <= 1000; size += 100) {
                String dataset = "GoCJ_Dataset_" + size + ".txt";
                for (Algoritma a : algoritma) {
                    for (int run = 1; run <= RUN; run++) {
                        Keluaran k = jalankan(a, dataset, false);
                        Hasil h = k.hasil();
                        w.printf(Locale.US, "gocj,%d,%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f%n",
                                size, a.nama(), run, h.makespan(), h.di(), h.ru(),
                                h.throughput(), h.art(), k.schedMs());
                        w.flush();
                        System.out.printf(Locale.US, "[OK] %s | %s | run %d | makespan %.2f%n",
                                dataset, a.nama(), run, h.makespan());
                    }
                }
            }
            System.out.println("\nSelesai. Hasil tersimpan di hasil.csv");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Keluaran jalankan(Algoritma algoritma, String namaDataset, boolean verbose) {
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        Datacenter dc1 = buatDatacenter(simulation, "DC1", 5);
        Datacenter dc2 = buatDatacenter(simulation, "DC2", 5);
        // VM 0-9 langsung diarahkan ke DC1 dan VM 10-19 ke DC2.
        broker.setDatacenterMapper((defaultDc, vm) -> vm.getId() < 10 ? dc1 : dc2);

        List<Vm> vmList = new ArrayList<>();
        vmList.addAll(buatVmHeterogen(10));
        vmList.addAll(buatVmHeterogen(10));
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = buatCloudlet(namaDataset);
        long t0 = System.nanoTime();
        Map<Cloudlet, Vm> penjadwalan = algoritma.scheduler().apply(cloudletList, vmList);
        double schedMs = (System.nanoTime() - t0) / 1e6;
        if (penjadwalan.size() != cloudletList.size()) {
            throw new IllegalStateException("Scheduler hanya menghasilkan " + penjadwalan.size()
                    + " assignment untuk " + cloudletList.size() + " cloudlet");
        }
        if (verbose) cetakDistribusi(namaDataset, algoritma.nama(), penjadwalan, vmList);
        penjadwalan.forEach(broker::bindCloudletToVm);
        broker.submitCloudletList(cloudletList);

        if (verbose) System.out.println("\n>>> Menjalankan " + algoritma.nama());
        simulation.start();
        List<Cloudlet> selesai = broker.getCloudletFinishedList();
        if (selesai.size() != cloudletList.size()) {
            throw new IllegalStateException("Cloudlet selesai " + selesai.size()
                    + " dari " + cloudletList.size() + " (" + namaDataset + ")");
        }
        if (verbose) {
            System.out.printf("Cloudlet selesai: %d dari %d%n", selesai.size(), cloudletList.size());
            MetricsCalculator.cetakRingkasan(algoritma.nama(), selesai, vmList);
        }
        return new Keluaran(hitungMetrik(selesai, vmList), schedMs);
    }

    /** Rumus identik dengan MetricsCalculator.cetakRingkasan, tapi mengembalikan angka. */
    private static Hasil hitungMetrik(List<Cloudlet> selesai, List<Vm> semuaVm) {
        double makespan = selesai.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);

        Map<Vm, Double> bebanPerVm = new HashMap<>();
        semuaVm.forEach(vm -> bebanPerVm.put(vm, 0.0));
        for (Cloudlet cl : selesai) {
            bebanPerVm.merge(cl.getVm(), cl.getFinishTime() - cl.getStartTime(), Double::sum);
        }
        double bMax = bebanPerVm.values().stream().mapToDouble(v -> v).max().orElse(0);
        double bMin = bebanPerVm.values().stream().mapToDouble(v -> v).min().orElse(0);
        double bAvg = bebanPerVm.values().stream().mapToDouble(v -> v).average().orElse(1);
        double di = (bAvg == 0) ? 0 : (bMax - bMin) / bAvg;

        double throughput = (makespan == 0) ? 0 : selesai.size() / makespan;
        double art = selesai.stream()
                .mapToDouble(cl -> cl.getFinishTime() - cl.getSubmissionDelay())
                .average().orElse(0);

        long totalPes = semuaVm.stream().mapToLong(Vm::getPesNumber).sum();
        double totalCpu = bebanPerVm.values().stream().mapToDouble(Double::doubleValue).sum();
        double ru = (makespan == 0 || totalPes == 0) ? 0 : totalCpu / (totalPes * makespan) * 100;

        return new Hasil(makespan, di, ru, throughput, art);
    }

    private static void cetakDistribusi(String namaDataset, String namaAlgoritma,
                                        Map<Cloudlet, Vm> penjadwalan, List<Vm> vmList) {
        Map<Vm, Integer> jumlahPerVm = new LinkedHashMap<>();
        Map<Vm, Long> miPerVm = new LinkedHashMap<>();
        vmList.forEach(vm -> {
            jumlahPerVm.put(vm, 0);
            miPerVm.put(vm, 0L);
        });
        penjadwalan.forEach((cloudlet, vm) -> {
            jumlahPerVm.merge(vm, 1, Integer::sum);
            miPerVm.merge(vm, cloudlet.getLength(), Long::sum);
        });

        System.out.printf("Assignment %s [%s]:%n", namaAlgoritma, namaDataset);
        for (int i = 0; i < vmList.size(); i++) {
            Vm vm = vmList.get(i);
            System.out.printf("  VM-%02d (%.0f MIPS, %d PE): %d task, %,d MI%n",
                    i, vm.getMips(), vm.getPesNumber(), jumlahPerVm.get(vm), miPerVm.get(vm));
        }
    }

    private static List<Cloudlet> buatCloudlet(String namaDataset) {
        List<Cloudlet> daftar = new ArrayList<>();
        try {
            List<Long> lengths = GoCJLoader.loadFromResource("Dataset_GoCJ/" + namaDataset);
            // Ambil angka dari nama file (GoCJ_Dataset_300.txt -> 300) untuk validasi jumlah baris.
            int expected = Integer.parseInt(namaDataset.replaceAll("\\D+", ""));
            if (lengths.size() != expected) {
                throw new IllegalArgumentException("Jumlah task tidak sesuai: " + lengths.size()
                        + ", seharusnya " + expected);
            }
            for (int i = 0; i < lengths.size(); i++) {
                long length = lengths.get(i);
                // ID wajib unik, kalau tidak HashMap hasil scheduler menganggap beberapa cloudlet sama.
                Cloudlet cloudlet = new CloudletSimple(i, length, 1);
                cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
                cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.10));
                cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.10));
                daftar.add(cloudlet);
            }
            return daftar;
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membaca dataset GoCJ: " + namaDataset, e);
        }
    }

    private static Datacenter buatDatacenter(CloudSimPlus simulation, String nama, int jumlahHost) {
        List<Host> hostList = new ArrayList<>();
        int[] tipeHost = {0, 1, 2, 1, 2};
        for (int i = 0; i < jumlahHost; i++) hostList.add(buatHost(tipeHost[i % tipeHost.length]));

        // Membatasi 10 VM pada setiap datacenter sesuai desain project.
        VmAllocationPolicySimple policy = new VmAllocationPolicySimple((p, vm) -> {
            int jumlahVm = p.getHostList().stream().mapToInt(host -> host.getVmList().size()).sum();
            if (jumlahVm >= 10) return Optional.empty();
            return p.getHostList().stream().filter(host -> host.isSuitableForVm(vm)).findFirst();
        });
        Datacenter dc = new DatacenterSimple(simulation, hostList, policy);
        dc.setName(nama);
        return dc;
    }

    private static Host buatHost(int tipe) {
        int core = tipe == 0 ? 2 : tipe == 1 ? 4 : 8;
        double mips = tipe == 0 ? 2000 : tipe == 1 ? 3000 : 4000;
        long ram = tipe == 0 ? 4096 : tipe == 1 ? 8192 : 16384;
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < core; i++) peList.add(new PeSimple(mips));
        Host host = new HostSimple(ram, 10_000, 1_000_000, peList);
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    private static List<Vm> buatVmHeterogen(int jumlahVm) {
        List<Vm> daftar = new ArrayList<>();
        for (int i = 0; i < jumlahVm; i++) {
            int tipe = i % 3;
            double mips = tipe == 0 ? 1800 : tipe == 1 ? 2800 : 3800;
            long pes = tipe == 0 ? 1 : tipe == 1 ? 2 : 4;
            long ram = tipe == 0 ? 1024 : tipe == 1 ? 2048 : 4096;
            Vm vm = new VmSimple(mips, pes);
            vm.setRam(ram).setBw(1000).setSize(10_000);
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            daftar.add(vm);
        }
        return daftar;
    }
}
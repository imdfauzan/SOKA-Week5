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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/** Runner sederhana: Sufferage sebagai algoritma utama dan Round Robin sebagai baseline. */
public class Kelompok7Simulation {
    private record Algoritma(String nama, BiFunction<List<Cloudlet>, List<Vm>, Map<Cloudlet, Vm>> scheduler) { }

    public static void main(String[] args) {
        List<Algoritma> algoritma = List.of(
                new Algoritma("Sufferage (LBMM)", SufferageScheduler::schedule),
                new Algoritma("Round Robin (baseline)", RoundRobinScheduler::schedule)
        );
        for (String dataset : List.of("GoCJ_Dataset_500.txt", "GoCJ_Dataset_1000.txt")) {
            System.out.println("\n================ DATASET: " + dataset + " ================");
            for (Algoritma a : algoritma) jalankan(a, dataset);
        }
    }

    private static void jalankan(Algoritma algoritma, String namaDataset) {
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);
        Datacenter dc1 = buatDatacenter(simulation, "DC1", 5);
        Datacenter dc2 = buatDatacenter(simulation, "DC2", 5);
        // VM 0-9 langsung diarahkan ke DC1 dan VM 10-19 ke DC2.
        // Dengan begitu broker tidak perlu mencoba VM 10-19 ke DC1 terlebih dahulu.
        broker.setDatacenterMapper((defaultDc, vm) -> vm.getId() < 10 ? dc1 : dc2);

        List<Vm> vmList = new ArrayList<>();
        vmList.addAll(buatVmHeterogen(10));
        vmList.addAll(buatVmHeterogen(10));
        broker.submitVmList(vmList);

        List<Cloudlet> cloudletList = buatCloudlet(namaDataset);
        Map<Cloudlet, Vm> penjadwalan = algoritma.scheduler().apply(cloudletList, vmList);
        if (penjadwalan.size() != cloudletList.size()) {
            throw new IllegalStateException("Scheduler hanya menghasilkan " + penjadwalan.size()
                    + " assignment untuk " + cloudletList.size() + " cloudlet");
        }
        cetakDistribusi(namaDataset, algoritma.nama(), penjadwalan, vmList);
        penjadwalan.forEach(broker::bindCloudletToVm);
        broker.submitCloudletList(cloudletList);

        System.out.println("\n>>> Menjalankan " + algoritma.nama());
        simulation.start();
        List<Cloudlet> selesai = broker.getCloudletFinishedList();
        System.out.printf("Cloudlet selesai: %d dari %d%n", selesai.size(), cloudletList.size());
        MetricsCalculator.cetakRingkasan(algoritma.nama(), selesai, vmList);
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
            int expected = namaDataset.contains("500") ? 500 : namaDataset.contains("1000") ? 1000 : -1;
            if (expected > 0 && lengths.size() != expected) {
                throw new IllegalArgumentException("Jumlah task tidak sesuai: " + lengths.size()
                        + ", seharusnya " + expected);
            }
            for (int i = 0; i < lengths.size(); i++) {
                long length = lengths.get(i);
                // ID wajib unik. Tanpa ID unik, HashMap hasil scheduler dapat
                // menganggap beberapa cloudlet sebagai key yang sama sehingga
                // hanya sebagian task yang benar-benar dibind ke scheduler.
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

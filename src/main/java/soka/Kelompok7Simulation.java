package soka;

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
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import soka.scheduler.RoundRobinScheduler;
import soka.scheduler.SufferageScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SOKA Kelompok 7 - Simulasi PANCASILA
 * Sesuai Desain Project (slide bagian B):
 *   - 2 Datacenter
 *   - 5 Host per Datacenter (total 10 Host), heterogen 3 tipe: A (rendah), B (sedang), C (tinggi)
 *   - 20 VM (10 per Datacenter), heterogen mengikuti tipe host
 *   - Cloudlet dari dataset GoCJ (500-1000 task)
 *   - Objektif: minimize Makespan & minimize Degree of Imbalance (DI)
 *
 * Cara jalankan:
 *   mvn clean package
 *   java -jar target/soka-simulasi.jar
 */
public class Kelompok7Simulation {

    private static final int JUMLAH_CLOUDLET = 500; // sesuai slide: 500-1000

    public static void main(String[] args) {
        CloudSimPlus simulation = new CloudSimPlus();

        // ===================== 1. DATACENTER & HOST (arsitektur B2) =====================
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        Datacenter dc1 = buatDatacenter(simulation, "DC1", 5);
        Datacenter dc2 = buatDatacenter(simulation, "DC2", 5);
        System.out.println("Datacenter dibuat: " + dc1.getName() + " & " + dc2.getName()
                + " (masing-masing 5 Host, total 10 Host heterogen)");

        // ===================== 2. VIRTUAL MACHINE (10 per Datacenter = 20 total) =====================
        List<Vm> vmList = new ArrayList<>();
        vmList.addAll(buatVmHeterogen(10)); // untuk DC1
        vmList.addAll(buatVmHeterogen(10)); // untuk DC2
        broker.submitVmList(vmList);
        System.out.println("Total VM dibuat: " + vmList.size());

        // ===================== 3. CLOUDLET dari dataset GoCJ =====================
        List<Long> panjangTask = GoCJLoader.generateSyntheticGoCJLike(JUMLAH_CLOUDLET, 42);
        // Ganti baris di atas dengan ini kalau sudah punya file dataset asli:
        // List<Long> panjangTask = GoCJLoader.loadFromFile("src/main/resources/GoCJ_Dataset_500.txt");

        List<Cloudlet> cloudletList = new ArrayList<>();
        for (long length : panjangTask) {
            Cloudlet cloudlet = new CloudletSimple(length, 1); // 1 PE per cloudlet
            // CPU memakai penuh, tetapi setiap task hanya memakai 10% RAM dan
            // bandwidth VM. setUtilizationModel(...) tidak dipakai karena ia
            // juga mengatur RAM/BW menjadi 100% dan membuat task lain tertahan.
            cloudlet.setUtilizationModelCpu(new UtilizationModelFull());
            cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.10));
            cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.10));
            cloudletList.add(cloudlet);
        }
        System.out.println("Total Cloudlet (task) dari GoCJ: " + cloudletList.size());

        // ===================== 4. JALANKAN ALGORITMA: SUFFERAGE / LBMM =====================
        Map<Cloudlet, Vm> pemetaanSufferage = SufferageScheduler.schedule(cloudletList, vmList);
        for (Map.Entry<Cloudlet, Vm> entry : pemetaanSufferage.entrySet()) {
            broker.bindCloudletToVm(entry.getKey(), entry.getValue());
        }
        broker.submitCloudletList(cloudletList);

        System.out.println("\n>>> Menjalankan simulasi dengan algoritma Sufferage (LBMM)...\n");
        simulation.start();

        List<Cloudlet> selesai = broker.getCloudletFinishedList();
        System.out.printf("Cloudlet selesai: %d dari %d%n", selesai.size(), cloudletList.size());
        MetricsCalculator.cetakRingkasan("Sufferage Heuristic (LBMM)", selesai);

        // Catatan: untuk membandingkan dengan Round Robin, CSO, COA, atau Hybrid,
        // jalankan simulasi terpisah (buat CloudSimPlus baru) dengan mengganti baris
        // SufferageScheduler.schedule(...) menjadi RoundRobinScheduler.schedule(...)
        // atau kelas scheduler lain yang kamu buat dengan pola yang sama.
    }

    /** Membuat 1 Datacenter berisi sejumlah Host heterogen (tipe A, B, C bergantian). */
    private static Datacenter buatDatacenter(CloudSimPlus simulation, String nama, int jumlahHost) {
        List<Host> hostList = new ArrayList<>();
        // 1 Host A, 2 Host B, dan 2 Host C per datacenter = 26 PE/DC.
        // Kapasitas ini cukup untuk 10 VM heterogen yang membutuhkan total 22 PE.
        int[] tipeHost = {0, 1, 2, 1, 2};
        for (int i = 0; i < jumlahHost; i++) {
            hostList.add(buatHost(tipeHost[i % tipeHost.length]));
        }
        Datacenter dc = new DatacenterSimple(simulation, hostList);
        dc.setName(nama);
        return dc;
    }

    /**
     * Membuat 1 Host sesuai salah satu dari 3 tipe spesifikasi di slide B2:
     * Tipe A (rendah): 2 core, 2000 MIPS/core, 4 GB RAM
     * Tipe B (sedang): 4 core, 3000 MIPS/core, 8 GB RAM
     * Tipe C (tinggi): 8 core, 4000 MIPS/core, 16 GB RAM
     */
    private static Host buatHost(int tipe) {
        int jumlahCore;
        double mipsPerCore;
        long ramMB;
        switch (tipe) {
            case 0 -> { jumlahCore = 2; mipsPerCore = 2000; ramMB = 4096; }
            case 1 -> { jumlahCore = 4; mipsPerCore = 3000; ramMB = 8192; }
            default -> { jumlahCore = 8; mipsPerCore = 4000; ramMB = 16384; }
        }
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < jumlahCore; i++) {
            peList.add(new PeSimple(mipsPerCore));
        }
        long bwMbps = 10_000; // bandwidth host
        long storageMB = 1_000_000; // storage host

        Host host = new HostSimple(ramMB, bwMbps, storageMB, peList);
        host.setVmScheduler(new VmSchedulerTimeShared());
        return host;
    }

    /**
     * Membuat sejumlah VM heterogen, proporsional mengikuti 3 tipe host
     * (supaya VM yang dibuat realistis sesuai kapasitas host yang menampungnya).
     */
    private static List<Vm> buatVmHeterogen(int jumlahVm) {
        List<Vm> daftarVm = new ArrayList<>();
        for (int i = 0; i < jumlahVm; i++) {
            int tipe = i % 3;
            double mips;
            long pes;
            long ramMB;
            switch (tipe) {
                case 0 -> { mips = 1800; pes = 1; ramMB = 1024; }  // VM kecil
                case 1 -> { mips = 2800; pes = 2; ramMB = 2048; }  // VM sedang
                default -> { mips = 3800; pes = 4; ramMB = 4096; } // VM besar
            }
            Vm vm = new VmSimple(mips, pes);
            vm.setRam(ramMB).setBw(1000).setSize(10_000);
            // Space-shared menjaga cloudlet yang menunggu tetap memiliki event
            // berikutnya. Dengan time-shared, CloudSim Plus 8.5.4 dapat menutup
            // VM setelah cloudlet pertama ketika banyak task terantre.
            vm.setCloudletScheduler(new CloudletSchedulerSpaceShared());
            daftarVm.add(vm);
        }
        return daftarVm;
    }
}

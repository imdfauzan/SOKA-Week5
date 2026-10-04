package soka.scheduler;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementasi algoritma heuristik Sufferage (dasar dari LBMM - Load Balanced Min-Min).
 *
 * Cara kerja:
 * 1. Untuk setiap cloudlet yang BELUM dijadwalkan, hitung estimasi Completion Time (CT)
 *    di setiap slot PE VM = waktu slot tersebut "siap" + (panjang cloudlet / MIPS VM).
 * 2. Hitung "sufferage value" = CT tercepat kedua - CT tercepat pertama.
 *    Nilai ini menandakan seberapa besar "kerugian" kalau cloudlet ini TIDAK
 *    mendapat VM terbaiknya.
 * 3. Pilih cloudlet dengan sufferage value TERBESAR, lalu tetapkan ke VM
 *    tercepatnya. Update readyTime VM tersebut.
 * 4. Ulangi sampai semua cloudlet mendapat VM.
 *
 * Ini membuat task yang "kritis" (hanya cocok baik di 1 VM tertentu) diprioritaskan
 * duluan, sementara task yang fleksibel (sama saja di VM mana pun) dibiarkan
 * menunggu -- inilah yang membedakannya dari Min-Min biasa, dan menjadi dasar
 * mekanisme load balancing pada varian LBMM.
 */
public class SufferageScheduler {

    /**
     * @param cloudlets daftar cloudlet yang akan dijadwalkan
     * @param vmList    daftar VM yang tersedia (boleh berasal dari >1 datacenter)
     * @return Map Cloudlet -> Vm hasil pemetaan algoritma Sufferage
     */
    public static Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vmList) {
        Map<Cloudlet, Vm> hasil = new HashMap<>();
        // CloudletSchedulerSpaceShared dapat menjalankan beberapa cloudlet
        // secara bersamaan pada VM yang memiliki lebih dari satu PE.
        // Karena itu ready time disimpan per slot PE, bukan satu nilai per VM.
        Map<Vm, List<Double>> readyTime = new HashMap<>();
        for (Vm vm : vmList) {
            List<Double> slots = new ArrayList<>();
            for (int i = 0; i < vm.getPesNumber(); i++) slots.add(0.0);
            readyTime.put(vm, slots);
        }

        List<Cloudlet> belumDijadwalkan = new ArrayList<>(cloudlets);

        while (!belumDijadwalkan.isEmpty()) {
            Cloudlet cloudletTerpilih = null;
            Vm vmTerbaikUntukTerpilih = null;
            double sufferageTerbesar = -1;
            double ctTerbaikUntukTerpilih = Double.MAX_VALUE;

            for (Cloudlet cl : belumDijadwalkan) {
                double ctTerbaik = Double.MAX_VALUE;
                double ctKedua = Double.MAX_VALUE;
                Vm vmTerbaik = null;

                for (Vm vm : vmList) {
                    double mips = vm.getMips();
                    double waktuEksekusi = cl.getLength() / mips;
                    List<Double> slots = readyTime.get(vm);
                    int slotTerbaik = 0;
                    for (int i = 1; i < slots.size(); i++) {
                        if (slots.get(i) < slots.get(slotTerbaik)) slotTerbaik = i;
                    }
                    double ct = slots.get(slotTerbaik) + waktuEksekusi;

                    if (ct < ctTerbaik) {
                        ctKedua = ctTerbaik;
                        ctTerbaik = ct;
                        vmTerbaik = vm;
                    } else if (ct < ctKedua) {
                        ctKedua = ct;
                    }
                }

                // Jika hanya ada 1 VM di sistem, sufferage = 0 (tidak ada pembanding)
                double sufferage = (ctKedua == Double.MAX_VALUE) ? 0 : (ctKedua - ctTerbaik);

                if (sufferage > sufferageTerbesar) {
                    sufferageTerbesar = sufferage;
                    cloudletTerpilih = cl;
                    vmTerbaikUntukTerpilih = vmTerbaik;
                    ctTerbaikUntukTerpilih = ctTerbaik;
                }
            }

            hasil.put(cloudletTerpilih, vmTerbaikUntukTerpilih);
            List<Double> slots = readyTime.get(vmTerbaikUntukTerpilih);
            int slot = 0;
            for (int i = 1; i < slots.size(); i++) {
                if (slots.get(i) < slots.get(slot)) slot = i;
            }
            slots.set(slot, ctTerbaikUntukTerpilih);
            belumDijadwalkan.remove(cloudletTerpilih);
        }

        return hasil;
    }
}

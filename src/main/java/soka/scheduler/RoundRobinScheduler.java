package soka.scheduler;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline pembanding: Round Robin.
 * Cloudlet dibagikan bergiliran ke VM tanpa mempertimbangkan
 * ukuran task atau kapasitas VM -- paling sederhana, dan hampir semua
 * studi (termasuk yang direview di paper Devi et al. 2024) memakainya
 * sebagai pembanding "paling dasar".
 */
public class RoundRobinScheduler {

    public static Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vmList) {
        Map<Cloudlet, Vm> hasil = new HashMap<>();
        int idx = 0;
        for (Cloudlet cl : cloudlets) {
            Vm vm = vmList.get(idx % vmList.size());
            hasil.put(cl, vm);
            idx++;
        }
        return hasil;
    }
}

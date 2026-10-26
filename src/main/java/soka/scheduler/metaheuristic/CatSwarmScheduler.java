package soka.scheduler.metaheuristic;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import soka.scheduler.heuristic.SufferageScheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Cat Swarm Optimization (CSO) untuk assignment cloudlet ke VM.
 *
 * Posisi satu cat adalah array diskrit: posisi[i] menyimpan indeks VM
 * untuk cloudlet ke-i. Fitness dihitung dari makespan estimasi dengan
 * memperhatikan slot PE yang tersedia pada setiap VM.
 *
 * Implementasi ini memakai dua mode CSO:
 * seeking untuk eksplorasi perubahan assignment lokal dan tracing untuk
 * mengarahkan posisi cat menuju global best. Solusi Sufferage dimasukkan
 * sebagai seed agar optimisasi selalu memiliki kandidat heuristik yang valid.
 */
public final class CatSwarmScheduler {
    private static final int SWARM_SIZE = 24;
    private static final int ITERATIONS = 35;
    private static final double SEEKING_RATIO = 0.70;
    private static final int SEEKING_TRIALS = 4;

    private CatSwarmScheduler() {
    }

    /**
     * @param cloudlets daftar cloudlet yang akan dijadwalkan
     * @param vmList daftar VM yang tersedia
     * @return assignment cloudlet ke VM dengan makespan estimasi terbaik
     */
    public static Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vmList) {
        validateInput(cloudlets, vmList);
        if (cloudlets.isEmpty()) return new HashMap<>();

        SplittableRandom random = new SplittableRandom(seed(cloudlets, vmList));
        List<Cat> swarm = new ArrayList<>(SWARM_SIZE);
        int[] sufferageSeed = toPositions(
                cloudlets,
                vmList,
                SufferageScheduler.schedule(cloudlets, vmList)
        );
        swarm.add(new Cat(sufferageSeed, evaluate(sufferageSeed, cloudlets, vmList)));

        int[] roundRobinSeed = new int[cloudlets.size()];
        for (int i = 0; i < roundRobinSeed.length; i++) {
            roundRobinSeed[i] = i % vmList.size();
        }
        swarm.add(new Cat(roundRobinSeed, evaluate(roundRobinSeed, cloudlets, vmList)));

        while (swarm.size() < SWARM_SIZE) {
            int[] position = randomPosition(cloudlets.size(), vmList.size(), random);
            swarm.add(new Cat(position, evaluate(position, cloudlets, vmList)));
        }

        Cat globalBest = copyOf(bestOf(swarm));
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            for (Cat cat : swarm) {
                if (random.nextDouble() < SEEKING_RATIO) {
                    seek(cat, cloudlets, vmList, random);
                } else {
                    trace(cat, globalBest.position(), vmList.size(), random);
                    cat.setFitness(evaluate(cat.position(), cloudlets, vmList));
                }
                if (cat.fitness() < globalBest.fitness()) {
                    globalBest = copyOf(cat);
                }
            }
        }

        return toAssignment(globalBest.position(), cloudlets, vmList);
    }

    private static void seek(Cat cat, List<Cloudlet> cloudlets, List<Vm> vmList,
                             SplittableRandom random) {
        Cat bestCandidate = copyOf(cat);
        for (int trial = 0; trial < SEEKING_TRIALS; trial++) {
            int[] candidatePosition = cat.position().clone();
            int changes = 1 + random.nextInt(Math.max(1, candidatePosition.length / 20 + 1));
            for (int i = 0; i < changes; i++) {
                int task = random.nextInt(candidatePosition.length);
                candidatePosition[task] = random.nextInt(vmList.size());
            }
            double fitness = evaluate(candidatePosition, cloudlets, vmList);
            if (fitness < bestCandidate.fitness()) {
                bestCandidate = new Cat(candidatePosition, fitness);
            }
        }
        cat.setPosition(bestCandidate.position());
        cat.setFitness(bestCandidate.fitness());
    }

    private static void trace(Cat cat, int[] globalBest, int vmCount,
                              SplittableRandom random) {
        int[] position = cat.position();
        for (int i = 0; i < position.length; i++) {
            if (position[i] != globalBest[i] && random.nextDouble() < 0.45) {
                position[i] = globalBest[i];
            } else if (random.nextDouble() < 0.05) {
                position[i] = random.nextInt(vmCount);
            }
        }
    }

    private static double evaluate(int[] position, List<Cloudlet> cloudlets, List<Vm> vmList) {
        List<double[]> readyTimes = new ArrayList<>(vmList.size());
        for (Vm vm : vmList) {
            readyTimes.add(new double[Math.toIntExact(vm.getPesNumber())]);
        }

        double makespan = 0;
        for (int task = 0; task < cloudlets.size(); task++) {
            Vm vm = vmList.get(position[task]);
            double[] slots = readyTimes.get(position[task]);
            int slot = 0;
            for (int i = 1; i < slots.length; i++) {
                if (slots[i] < slots[slot]) slot = i;
            }
            double finish = slots[slot] + cloudlets.get(task).getLength() / vm.getMips();
            slots[slot] = finish;
            makespan = Math.max(makespan, finish);
        }
        return makespan;
    }

    private static int[] toPositions(List<Cloudlet> cloudlets, List<Vm> vmList,
                                     Map<Cloudlet, Vm> assignment) {
        int[] positions = new int[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++) {
            Vm vm = assignment.get(cloudlets.get(i));
            int index = vmList.indexOf(vm);
            if (index < 0) {
                throw new IllegalStateException("Sufferage menghasilkan VM yang tidak tersedia");
            }
            positions[i] = index;
        }
        return positions;
    }

    private static Map<Cloudlet, Vm> toAssignment(int[] positions, List<Cloudlet> cloudlets,
                                                   List<Vm> vmList) {
        Map<Cloudlet, Vm> assignment = new HashMap<>();
        for (int i = 0; i < cloudlets.size(); i++) {
            assignment.put(cloudlets.get(i), vmList.get(positions[i]));
        }
        return assignment;
    }

    private static int[] randomPosition(int taskCount, int vmCount, SplittableRandom random) {
        int[] position = new int[taskCount];
        for (int i = 0; i < taskCount; i++) position[i] = random.nextInt(vmCount);
        return position;
    }

    private static Cat bestOf(List<Cat> swarm) {
        Cat best = swarm.get(0);
        for (Cat cat : swarm) {
            if (cat.fitness() < best.fitness()) best = cat;
        }
        return best;
    }

    private static Cat copyOf(Cat cat) {
        return new Cat(cat.position().clone(), cat.fitness());
    }

    private static long seed(List<Cloudlet> cloudlets, List<Vm> vmList) {
        long seed = 0x9E3779B97F4A7C15L ^ cloudlets.size();
        for (Cloudlet cloudlet : cloudlets) seed = seed * 31 + cloudlet.getLength();
        for (Vm vm : vmList) seed = seed * 31 + vm.getPesNumber();
        return seed;
    }

    private static void validateInput(List<Cloudlet> cloudlets, List<Vm> vmList) {
        if (cloudlets == null || vmList == null || vmList.isEmpty()) {
            throw new IllegalArgumentException("Cloudlet dan VM tidak boleh null, VM minimal satu");
        }
        for (Vm vm : vmList) {
            if (vm.getPesNumber() <= 0 || vm.getMips() <= 0) {
                throw new IllegalArgumentException("VM harus memiliki MIPS dan PE positif");
            }
        }
    }

    private static final class Cat {
        private int[] position;
        private double fitness;

        private Cat(int[] position, double fitness) {
            this.position = position;
            this.fitness = fitness;
        }

        private int[] position() {
            return position;
        }

        private double fitness() {
            return fitness;
        }

        private void setPosition(int[] position) {
            this.position = position;
        }

        private void setFitness(double fitness) {
            this.fitness = fitness;
        }
    }
}

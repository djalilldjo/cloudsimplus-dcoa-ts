package org.cloudsimplus.examples.dynamic.teststatic;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class DCOAMapper {
    private static final int COATI_POPULATION = 16;
    private static final int MAX_ITERATIONS = 100;
    private final List<Vm> vmList;
    private final Random random = new Random();

    public DCOAMapper(List<Vm> vmList) { this.vmList = vmList; }

 // ⊛ operator: Swap a percentage of assignments
    void swapOperator(int[] assignment, double percentage) {
        int swaps = (int)(assignment.length * percentage);
        Random rand = new Random();
        for (int i = 0; i < swaps; i++) {
            int idx1 = rand.nextInt(assignment.length);
            int idx2 = rand.nextInt(assignment.length);
            int temp = assignment[idx1];
            assignment[idx1] = assignment[idx2];
            assignment[idx2] = temp;
        }
    }

    // ⊝ operator: Combine two assignments
    int[] combineOperator(int[] a, int[] b) {
        int[] result = new int[a.length];
        Random rand = new Random();
        for (int i = 0; i < a.length; i++) {
            result[i] = rand.nextBoolean() ? a[i] : b[i];
        }
        return result;
    }

    // ⊕ operator: Crossover two assignments
    int[] crossoverOperator(int[] a, int[] b) {
        int[] result = new int[a.length];
        int mid = a.length / 2;
        for (int i = 0; i < mid; i++) result[i] = a[i];
        for (int i = mid; i < a.length; i++) result[i] = b[i];
        return result;
    }

    
    public int findBestVmIndex(Cloudlet cloudlet) {
        List<Coati> coatis = new ArrayList<>(COATI_POPULATION);
        for (int i = 0; i < COATI_POPULATION; i++) {
            int vmIndex = random.nextInt(vmList.size());
            coatis.add(new Coati(vmIndex, fitness(vmList.get(vmIndex), cloudlet)));
        }
        Coati bestCoati = getBestCoati(coatis);
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            int half = COATI_POPULATION / 2;
            for (int i = 0; i < half; i++) {
                int I = 1 + random.nextInt(2);
                int oldVm = coatis.get(i).vmIndex;
                int newVm = (int) Math.round(oldVm + random.nextDouble() * (bestCoati.vmIndex - I * oldVm));
                newVm = Math.floorMod(newVm, vmList.size());
                double newFitness = fitness(vmList.get(newVm), cloudlet);
                if (newFitness < coatis.get(i).fitness) {
                    coatis.get(i).vmIndex = newVm;
                    coatis.get(i).fitness = newFitness;
                }
            }
            for (int i = half; i < COATI_POPULATION; i++) {
                int randomVm = random.nextInt(vmList.size());
                double randomFitness = fitness(vmList.get(randomVm), cloudlet);
                int oldVm = coatis.get(i).vmIndex;
                int newVm;
                if (randomFitness < coatis.get(i).fitness) {
                    newVm = randomVm;
                } else {
                    newVm = (int) Math.round(oldVm + random.nextDouble() * (oldVm - randomVm));
                    newVm = Math.floorMod(newVm, vmList.size());
                }
                double newFitness = fitness(vmList.get(newVm), cloudlet);
                if (newFitness < coatis.get(i).fitness) {
                    coatis.get(i).vmIndex = newVm;
                    coatis.get(i).fitness = newFitness;
                }
            }
            for (int i = 0; i < COATI_POPULATION; i++) {
                int oldVm = coatis.get(i).vmIndex;
                int neighborVm = (oldVm + (random.nextBoolean() ? 1 : -1) + vmList.size()) % vmList.size();
                double neighborFitness = fitness(vmList.get(neighborVm), cloudlet);
                if (neighborFitness < coatis.get(i).fitness) {
                    coatis.get(i).vmIndex = neighborVm;
                    coatis.get(i).fitness = neighborFitness;
                }
            }
            bestCoati = getBestCoati(coatis);
        }
        return bestCoati.vmIndex;
    }

    private double fitness(Vm vm, Cloudlet cloudlet) {
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
        double finishTime = execTime;
        double deadline = cloudlet instanceof PrioritizedCloudlet ? ((PrioritizedCloudlet) cloudlet).getDeadline() : 0;
        double violationPenalty = (deadline > 0 && finishTime > deadline) ? (finishTime - deadline) : 0;
        if (vm.getHost() == null || vm.getHost().getPowerModel() == null) {
            return execTime + violationPenalty * 1000;
        }
        var host = vm.getHost();
        PowerModel powerModel = host.getPowerModel();
        double currentUtil = host.getCpuUtilizationStats().getMean();
        double vmUtilIncrease = (double) cloudlet.getPesNumber() * vm.getMips() / host.getTotalMipsCapacity();
        double projectedUtil = Math.min(1.0, currentUtil + vmUtilIncrease);
        double avgPower = powerModel.getPower();
        double energy = avgPower * execTime;
        double alpha = 1.0, beta = 1000.0;
        return alpha * energy + beta * violationPenalty;
    }

    private Coati getBestCoati(List<Coati> coatis) {
        return Collections.min(coatis, Comparator.comparingDouble(c -> c.fitness));
    }

    private static class Coati {
        int vmIndex;
        double fitness;
        Coati(int vmIndex, double fitness) { this.vmIndex = vmIndex; this.fitness = fitness; }
    }
}

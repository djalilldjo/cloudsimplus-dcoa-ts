package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Discrete Coati Optimization Algorithm (COA) Broker for cloud task scheduling.
 * Each coati represents a candidate VM assignment for the current cloudlet.
 * Fitness combines energy consumption and deadline violation.
 */
public class DCOABroker extends DatacenterBrokerSimple {

    private static final int COATI_POPULATION = 16;
    private static final int MAX_ITERATIONS = 100;
    private final Random random = new Random();

    public DCOABroker(CloudSimPlus simulation) {
        super(simulation);
    }

    @Override
    protected Vm defaultVmMapper(final Cloudlet cloudlet) {
        final List<Vm> vmList = getVmExecList();
        if (vmList.isEmpty()) {
            throw new IllegalStateException("No VMs available to map cloudlets.");
        }

        // 1. Initialize population: each coati is a candidate VM assignment
        List<Coati> coatis = new ArrayList<>(COATI_POPULATION);
        for (int i = 0; i < COATI_POPULATION; i++) {
            int vmIndex = random.nextInt(vmList.size());
            coatis.add(new Coati(vmIndex, fitness(vmList.get(vmIndex), cloudlet)));
        }

        Coati bestCoati = getBestCoati(coatis);

        // 2. Main COA loop
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // --- Phase 1: Exploration (Hunting Iguanas) ---
            int half = COATI_POPULATION / 2;
            // First half: move towards best (the "iguana in the tree")
            for (int i = 0; i < half; i++) {
                int I = 1 + random.nextInt(2); // I in {1,2}
                int oldVm = coatis.get(i).vmIndex;
                int newVm = (int) Math.round(oldVm + random.nextDouble() * (bestCoati.vmIndex - I * oldVm));
                newVm = Math.floorMod(newVm, vmList.size());
                double newFitness = fitness(vmList.get(newVm), cloudlet);
                if (newFitness < coatis.get(i).fitness) {
                    coatis.get(i).vmIndex = newVm;
                    coatis.get(i).fitness = newFitness;
                }
            }
            // Second half: move towards random (the "iguana on the ground")
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

            // --- Phase 2: Exploitation (Escaping Predators) ---
            for (int i = 0; i < COATI_POPULATION; i++) {
                int oldVm = coatis.get(i).vmIndex;
                // Move to a neighbor VM (local move)
                int neighborVm = (oldVm + (random.nextBoolean() ? 1 : -1) + vmList.size()) % vmList.size();
                double neighborFitness = fitness(vmList.get(neighborVm), cloudlet);
                if (neighborFitness < coatis.get(i).fitness) {
                    coatis.get(i).vmIndex = neighborVm;
                    coatis.get(i).fitness = neighborFitness;
                }
            }
            // Update best
            bestCoati = getBestCoati(coatis);
        }

        return vmList.get(bestCoati.vmIndex);
    }

    /** Fitness: energy + deadline violation penalty (lower is better) */
    private double fitness(Vm vm, Cloudlet cloudlet) {
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
        double vmBusyTime = vm.getCloudletScheduler().getCloudletWaitingList().stream()
                .mapToDouble(cl -> cl.getCloudletLength() / (cl.getPesNumber() * cl.getLastAllocatedMips()))
                .sum();
        double finishTime = vmBusyTime + execTime;

        // Deadline violation penalty
        double deadline = getDeadline(cloudlet);
        double violationPenalty = (deadline > 0 && finishTime > deadline) ? (finishTime - deadline) : 0;

        // Energy estimation
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
        // Weights: energy + heavy penalty for deadline violation
        double alpha = 1.0;
        double beta = 1000.0;
        return alpha * energy + beta * violationPenalty;
    }

    /** Extract deadline for PrioritizedCloudlet or return 0 */
    private double getDeadline(Cloudlet cloudlet) {
        try {
            if (cloudlet instanceof PrioritizedCloudlet) {
                return ((PrioritizedCloudlet) cloudlet).getDeadline();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Helper: find best coati in the population */
    private Coati getBestCoati(List<Coati> coatis) {
        return Collections.min(coatis, Comparator.comparingDouble(c -> c.fitness));
    }

    /** Coati agent: represents a candidate VM assignment */
    private static class Coati {
        int vmIndex;
        double fitness;
        Coati(int vmIndex, double fitness) {
            this.vmIndex = vmIndex;
            this.fitness = fitness;
        }
    }
}

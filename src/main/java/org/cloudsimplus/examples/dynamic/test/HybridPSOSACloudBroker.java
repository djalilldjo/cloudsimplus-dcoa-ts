package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Hybrid PSO-SA Broker for dynamic cloudlet scheduling in cloud-only environment.
 * Minimizes a fitness combining energy consumption and deadline violation.
 */
public class HybridPSOSACloudBroker extends DatacenterBrokerSimple {

    private static final int PSO_PARTICLE_COUNT = 100;
    private static final int PSO_ITERATIONS = 100;
    private static final double SA_INITIAL_TEMP = 10.0;
    private static final double SA_COOLING_RATE = 0.90;
    private static final int SA_ITERATIONS = 100;

    private final Random random = new Random();

    public HybridPSOSACloudBroker(CloudSimPlus simulation) {
        super(simulation);
    }

    @Override
    protected Vm defaultVmMapper(final Cloudlet cloudlet) {
        final List<Vm> vmList = getVmExecList();
        if (vmList.isEmpty()) {
            throw new IllegalStateException("No VMs available to map cloudlets.");
        }

        // --- PSO: Exploration ---
        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < PSO_PARTICLE_COUNT; i++) {
            int vmIndex = random.nextInt(vmList.size());
            particles.add(new Particle(vmIndex, fitness(vmList.get(vmIndex), cloudlet)));
        }

        for (int iter = 0; iter < PSO_ITERATIONS; iter++) {
            Particle globalBest = Collections.min(particles, Comparator.comparingDouble(p -> p.fitness));
            for (Particle p : particles) {
                // With probability, move towards global best or random
                if (random.nextDouble() < 0.7) {
                    p.vmIndex = globalBest.vmIndex;
                } else {
                    p.vmIndex = random.nextInt(vmList.size());
                }
                p.fitness = fitness(vmList.get(p.vmIndex), cloudlet);
            }
        }

        // --- SA: Exploitation ---
        Particle best = Collections.min(particles, Comparator.comparingDouble(p -> p.fitness));
        int currentVmIndex = best.vmIndex;
        double currentFitness = best.fitness;
        double temp = SA_INITIAL_TEMP;

        for (int i = 0; i < SA_ITERATIONS; i++) {
            int neighborVmIndex = random.nextInt(vmList.size());
            double neighborFitness = fitness(vmList.get(neighborVmIndex), cloudlet);
            double delta = neighborFitness - currentFitness;
            if (delta < 0 || Math.exp(-delta / temp) > random.nextDouble()) {
                currentVmIndex = neighborVmIndex;
                currentFitness = neighborFitness;
            }
            temp *= SA_COOLING_RATE;
        }

        return vmList.get(currentVmIndex);
    }

    /**
     * Fitness function combining estimated incremental energy and deadline violation penalty.
     * Lower fitness is better.
     */
    private double fitness(Vm vm, Cloudlet cloudlet) {
        // Estimate execution time on VM
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());

        // Estimate finish time = current VM busy time + execTime
        double vmBusyTime = vm.getCloudletScheduler().getCloudletWaitingList().stream()
                .mapToDouble(cl -> cl.getCloudletLength() / (cl.getPesNumber() * cl.getLastAllocatedMips()))
                .sum();
        double finishTime = vmBusyTime + execTime;

        // Deadline violation penalty (if PrioritizedCloudlet with deadline)
        double deadline = getDeadline(cloudlet);
        double violationPenalty = 0;
        if (deadline > 0 && finishTime > deadline) {
            violationPenalty = finishTime - deadline;
        }

        // Estimate incremental energy consumption (host-level)
        if (vm.getHost() == null || vm.getHost().getPowerModel() == null) {
            return execTime + violationPenalty * 1000; // fallback with heavy penalty
        }
        var host = vm.getHost();
        PowerModel powerModel = host.getPowerModel();

        double currentUtil = host.getCpuUtilizationStats().getMean();
        double vmUtilIncrease = (double) cloudlet.getPesNumber() * vm.getMips() / host.getTotalMipsCapacity();
        double projectedUtil = Math.min(1.0, currentUtil + vmUtilIncrease);
        double avgUtil = (currentUtil + projectedUtil) / 2.0;
        double avgPower = powerModel.getPower();
        double energy = avgPower * execTime;

        // Weight factors to balance energy and deadline violation (tune as needed)
        double alpha = 1.0;  // energy weight
        double beta = 1000;  // penalty weight (high to prioritize deadlines)

        return alpha * energy + beta * violationPenalty;
    }

    /** Helper to get deadline if set, else 0 */
    private double getDeadline(Cloudlet cloudlet) {
        if (cloudlet instanceof PrioritizedCloudlet) {
            return ((PrioritizedCloudlet) cloudlet).getDeadline();
        }
        return 0;
    }

    /** Particle class for PSO */
    private static class Particle {
        int vmIndex;
        double fitness;

        Particle(int vmIndex, double fitness) {
            this.vmIndex = vmIndex;
            this.fitness = fitness;
        }
    }
}

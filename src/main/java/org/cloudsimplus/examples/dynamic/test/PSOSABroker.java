package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Hybrid PSO-SA Broker for dynamic task scheduling with energy-aware fitness.
 * PSO explores the search space, SA exploits the best found solution.
 */
public class PSOSABroker extends DatacenterBrokerSimple {
    private static final int PSO_PARTICLE_COUNT = 800;
    private static final int PSO_ITERATIONS = 100;
    private static final double SA_INITIAL_TEMP = 100.0;
    private static final double SA_COOLING_RATE = 0.90;
    private static final int SA_ITERATIONS = 10;
    private final Random random = new Random();

    public PSOSABroker(CloudSimPlus simulation) {
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
            particles.add(new Particle(vmIndex, evaluate(vmList.get(vmIndex), cloudlet)));
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
                p.fitness = evaluate(vmList.get(p.vmIndex), cloudlet);
            }
        }

        // --- SA: Exploitation ---
        Particle best = Collections.min(particles, Comparator.comparingDouble(p -> p.fitness));
        int currentVmIndex = best.vmIndex;
        double currentFitness = best.fitness;
        double temp = SA_INITIAL_TEMP;

        for (int i = 0; i < SA_ITERATIONS; i++) {
            int neighborVmIndex = random.nextInt(vmList.size());
            double neighborFitness = evaluate(vmList.get(neighborVmIndex), cloudlet);
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
     * Fitness function: lower is better.
     * Estimates the incremental energy (Joules) for running the cloudlet on the candidate VM.
     */
    private double evaluate(Vm vm, Cloudlet cloudlet) {
        // Estimate execution time for the cloudlet on this VM
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());

        // Ensure the VM is allocated to a host with a power model
        if (vm.getHost() == null || vm.getHost().getPowerModel() == null) {
            return execTime; // fallback: just use execution time
        }

        var host = vm.getHost();
        var powerModel = host.getPowerModel();

        // Estimate current utilization (mean of recent stats)
        double currentUtil = host.getCpuUtilizationStats().getMean();
        // Estimate utilization after adding this cloudlet (simplified)
        double vmUtilIncrease = (double) cloudlet.getPesNumber() * vm.getMips() / host.getTotalMipsCapacity();
        double projectedUtil = Math.min(1.0, currentUtil + vmUtilIncrease);

        // Estimate average power during execution
        double avgUtil = (currentUtil + projectedUtil) / 2.0;
        double avgPower = powerModel.getPower(avgUtil); // in Watts

        // Energy = Power (W) * Time (s)
        double energy = avgPower * execTime;

        return energy; // Lower energy is better
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

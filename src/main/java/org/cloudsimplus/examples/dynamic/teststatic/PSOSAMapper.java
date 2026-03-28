package org.cloudsimplus.examples.dynamic.teststatic;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.power.models.PowerModel;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class PSOSAMapper {
    private static final int PSO_PARTICLE_COUNT = 24;
    private static final int PSO_ITERATIONS = 10;
    private static final double SA_INITIAL_TEMP = 100.0;
    private static final double SA_COOLING_RATE = 0.90;
    private static final int SA_ITERATIONS = 8;

    private final List<Vm> vmList;
    private final Random random = new Random();

    public PSOSAMapper(List<Vm> vmList) {
        this.vmList = vmList;
    }

    public int findBestVmIndex(Cloudlet cloudlet) {
        // --- PSO: Exploration ---
        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < PSO_PARTICLE_COUNT; i++) {
            int vmIndex = random.nextInt(vmList.size());
            particles.add(new Particle(vmIndex, evaluate(vmList.get(vmIndex), cloudlet)));
        }

        for (int iter = 0; iter < PSO_ITERATIONS; iter++) {
            Particle globalBest = Collections.min(particles, Comparator.comparingDouble(p -> p.fitness));
            for (Particle p : particles) {
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

        return currentVmIndex;
    }

    private double evaluate(Vm vm, Cloudlet cloudlet) {
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
        double finishTime = execTime;
        double deadline = (cloudlet instanceof PrioritizedCloudlet) ? ((PrioritizedCloudlet) cloudlet).getDeadline() : 0;
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

    private static class Particle {
        int vmIndex;
        double fitness;
        Particle(int vmIndex, double fitness) { this.vmIndex = vmIndex; this.fitness = fitness; }
    }
}

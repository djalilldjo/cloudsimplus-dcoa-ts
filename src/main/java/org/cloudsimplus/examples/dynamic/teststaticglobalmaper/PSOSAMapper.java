package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class PSOSAMapper {

    //private static final int PSO_PARTICLE_COUNT = 300;     // Experimentally tuned
    //private static final int PSO_ITERATIONS = 200;
    private final int population;
    private final int iterations;
    
    private static final double SA_INITIAL_TEMP = 100.0;
    private static final double SA_COOLING_RATE = 0.90;
    private static final int SA_ITERATIONS = 8;

    private final List<Vm> vmList;
    private final Random random;
    private final EnergyEstimator energyEstimator;

    public PSOSAMapper(List<Vm> vmList, int population, int iterations) {
        this(vmList, population, iterations, System.currentTimeMillis(), (cl, vm) -> (cl.getLength() / (vm.getMips() * vm.getPesNumber())) * 100.0);
    }

    public PSOSAMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
        this.vmList = vmList;
        this.population = population;
        this.iterations = iterations;
        this.random = new Random(seed);
        this.energyEstimator = energyEstimator;
    }

    public int[] findBestMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        int numCloudlets = cloudlets.size();
        int numVms = vms.size();
        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < this.population; i++) {
            int[] assignment = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++)
                assignment[j] = random.nextInt(numVms);
            particles.add(new Particle(assignment, evaluate(assignment, cloudlets, vms)));
        }

        for (int iter = 0; iter < this.iterations; iter++) {
            Particle globalBest = Collections.min(particles, Comparator.comparingDouble(p -> p.fitness));
            for (Particle p : particles) {
                if (random.nextDouble() < 0.7) {
                    System.arraycopy(globalBest.assignment, 0, p.assignment, 0, numCloudlets);
                } else {
                    for (int j = 0; j < numCloudlets; j++)
                        p.assignment[j] = random.nextInt(numVms);
                }
                p.fitness = evaluate(p.assignment, cloudlets, vms);
            }
        }

        // Simulated Annealing refinement
        Particle best = Collections.min(particles, Comparator.comparingDouble(p -> p.fitness));
        int[] current = Arrays.copyOf(best.assignment, numCloudlets);
        double currentFitness = best.fitness;
        double temp = SA_INITIAL_TEMP;
        for (int i = 0; i < SA_ITERATIONS; i++) {
            int[] neighbor = Arrays.copyOf(current, numCloudlets);
            int idx = random.nextInt(numCloudlets);
            neighbor[idx] = random.nextInt(numVms);
            double neighborFitness = evaluate(neighbor, cloudlets, vms);
            double delta = neighborFitness - currentFitness;
            if (delta < 0 || Math.exp(-delta / temp) > random.nextDouble()) {
                current = neighbor;
                currentFitness = neighborFitness;
            }
            temp *= SA_COOLING_RATE;
        }
        return current;
    }

    // Fitness: Explicit makespan + energy (weighted)
    private double evaluate(int[] assignment, List<Cloudlet> cloudlets, List<Vm> vms) {
        int numVms = vms.size();
        double[] vmFinishTimes = new double[numVms];
        double totalEnergy = 0;
        for (int i = 0; i < assignment.length; i++) {
            int vmIdx = assignment[i];
            Cloudlet cl = cloudlets.get(i);
            Vm vm = vms.get(vmIdx);
            double execTime = cl.getLength() / (vm.getMips() * vm.getPesNumber());
            vmFinishTimes[vmIdx] += execTime;
            // Energy estimation
            totalEnergy += energyEstimator.estimateEnergy(cl, vm);
        }
        double makespan = Arrays.stream(vmFinishTimes).max().orElse(0.0);
        double wEnergy = 0.2, wMakespan = 0.7;
        return wEnergy * totalEnergy + wMakespan * makespan;
    }

    private static class Particle {
        int[] assignment;
        double fitness;
        Particle(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
    }
}

package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class DTSOMapper {

    // private static final int TUNA_POPULATION = 300;
    // private static final int MAX_ITERATIONS = 800;

    private final int population;
    private final int iterations;

    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;

    public DTSOMapper(List<Vm> vmList, int population, int iterations) {
        this(vmList, population, iterations, System.currentTimeMillis(),
                (cl, vm) -> (cl.getLength() / (vm.getMips() * vm.getPesNumber())) * 100.0);
    }

    public DTSOMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
        this.vmList = vmList;
        this.population = population;
        this.iterations = iterations;
        this.random = new Random(seed);
        this.energyEstimator = energyEstimator;
    }

    /**
     * Main method to find the best mapping of cloudlets to VMs using DTSO.
     */
    public int[] findBestMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        int numCloudlets = cloudlets.size();
        int numVms = vms.size();

        // Initialize population
        List<Tuna> swarm = new ArrayList<>();
        for (int i = 0; i < this.population; i++) {
            int[] assign = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++)
                assign[j] = random.nextInt(numVms);
            swarm.add(new Tuna(assign, fitness(assign, cloudlets, vms)));
        }

        int[] globalBest = Arrays.copyOf(swarm.get(0).assignment, numCloudlets);
        double globalBestFitness = swarm.get(0).fitness;

        for (int iter = 0; iter < this.iterations; iter++) {
            Tuna iterBest = Collections.min(swarm, Comparator.comparingDouble(t -> t.fitness));
            if (iterBest.fitness < globalBestFitness) {
                globalBest = Arrays.copyOf(iterBest.assignment, numCloudlets);
                globalBestFitness = iterBest.fitness;
            }

            for (int i = 0; i < this.population; i++) {
                int[] newAssign = Arrays.copyOf(swarm.get(i).assignment, numCloudlets);

                // Discrete operators (⊛, ⊝, ⊕) as described in paper
                double opSeed = random.nextDouble();
                if (opSeed < 0.33) {
                    newAssign = swapOperator(newAssign, 0.2);
                } else if (opSeed < 0.66) {
                    newAssign = combineOperator(newAssign, globalBest);
                } else {
                    int randIdx = random.nextInt(this.population);
                    newAssign = crossoverOperator(newAssign, swarm.get(randIdx).assignment);
                }

                double newFitness = fitness(newAssign, cloudlets, vms);
                if (newFitness < swarm.get(i).fitness) {
                    swarm.get(i).assignment = newAssign;
                    swarm.get(i).fitness = newFitness;
                }
            }
        }
        Tuna best = Collections.min(swarm, Comparator.comparingDouble(t -> t.fitness));
        return best.assignment;
    }

    // Operator ⊛: swap percentage of assignments
    private int[] swapOperator(int[] assignment, double percent) {
        int[] arr = Arrays.copyOf(assignment, assignment.length);
        int n = (int) (arr.length * percent);
        for (int i = 0; i < n; i++) {
            int a = random.nextInt(arr.length);
            int b = random.nextInt(arr.length);
            int tmp = arr[a];
            arr[a] = arr[b];
            arr[b] = tmp;
        }
        return arr;
    }

    // Operator ⊝: combine elements from a & b
    private int[] combineOperator(int[] a, int[] b) {
        int[] result = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = random.nextBoolean() ? a[i] : b[i];
        }
        return result;
    }

    // Operator ⊕: crossover
    private int[] crossoverOperator(int[] a, int[] b) {
        int[] result = new int[a.length];
        int mid = a.length / 2;
        for (int i = 0; i < mid; i++)
            result[i] = a[i];
        for (int i = mid; i < a.length; i++)
            result[i] = b[i];
        return result;
    }

    private double fitness(int[] assignment, List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] vmFinish = new double[vms.size()];
        double totalEnergy = 0;
        for (int i = 0; i < assignment.length; i++) {
            int vmIdx = assignment[i];
            Cloudlet cl = cloudlets.get(i);
            Vm vm = vms.get(vmIdx);
            double exec = cl.getLength() / (vm.getMips() * vm.getPesNumber());
            vmFinish[vmIdx] += exec;
            // Estimate energy:
            totalEnergy += energyEstimator.estimateEnergy(cl, vm);
        }
        double makespan = Arrays.stream(vmFinish).max().orElse(0.0);
        // ADD: Makespan + energy with adjusted weights
        double w1 = 0.20, w2 = 0.80; // makespan=80%, energy=20%
        return w1 * totalEnergy + w2 * makespan;
    }

    private static class Tuna {
        int[] assignment;
        double fitness;

        Tuna(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
    }
}

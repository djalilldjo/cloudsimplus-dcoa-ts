package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class DBDEMapper {

    private final int population;
    private final int iterations;
    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;
    
    private double wEnergy = 0.2;
    private double wMakespan = 0.7;
    private double wViolation = 0.1;

    public DBDEMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
        this.vmList = vmList;
        this.population = population;
        this.iterations = iterations;
        this.random = new Random(seed);
        this.energyEstimator = energyEstimator;
    }

    public void setWeights(double e, double m, double v) {
        this.wEnergy = e;
        this.wMakespan = m;
        this.wViolation = v;
    }

    public int[] findBestMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        int numCloudlets = cloudlets.size();
        int numVms = vms.size();

        double F = 0.5; // Scaling factor equivalent
        double CR = 0.8; // Crossover probability

        List<Individual> pop = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            int[] assign = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++) assign[j] = random.nextInt(numVms);
            pop.add(new Individual(assign, fitness(assign, cloudlets, vms)));
        }

        Individual globalBest = Collections.min(pop, Comparator.comparingDouble(ind -> ind.fitness)).copy();

        for (int iter = 0; iter < iterations; iter++) {
            for (int i = 0; i < population; i++) {
                Individual current = pop.get(i);
                
                // Pick 3 random, distinct individuals
                int r1, r2, r3;
                do { r1 = random.nextInt(population); } while (r1 == i);
                do { r2 = random.nextInt(population); } while (r2 == i || r2 == r1);
                do { r3 = random.nextInt(population); } while (r3 == i || r3 == r1 || r3 == r2);

                Individual indR1 = pop.get(r1);
                Individual indR2 = pop.get(r2);
                Individual indR3 = pop.get(r3);

                int[] trialAssign = new int[numCloudlets];
                int jRand = random.nextInt(numCloudlets);

                for (int j = 0; j < numCloudlets; j++) {
                    if (random.nextDouble() <= CR || j == jRand) {
                        // Mutate: standard DE is v = x_r1 + F * (x_r2 - x_r3)
                        // Discrete approx: take x_r1, but with probability F take x_r2 or x_r3
                        if (random.nextDouble() < F) {
                            trialAssign[j] = (random.nextBoolean()) ? indR2.assignment[j] : indR3.assignment[j];
                        } else {
                            trialAssign[j] = indR1.assignment[j];
                        }
                    } else {
                        trialAssign[j] = current.assignment[j];
                    }
                }
                
                double trialFit = fitness(trialAssign, cloudlets, vms);
                if (trialFit < current.fitness) {
                    current.assignment = trialAssign;
                    current.fitness = trialFit;
                    if (trialFit < globalBest.fitness) {
                        globalBest = current.copy();
                    }
                }
            }
        }
        
        return globalBest.assignment;
    }

    private double fitness(int[] assignment, List<Cloudlet> cloudlets, List<Vm> vms) {
        double[] vmFinish = new double[vms.size()];
        double totalEnergy = 0, totalViolation = 0;
        for (int i = 0; i < assignment.length; i++) {
            int vmIdx = assignment[i];
            Cloudlet cl = cloudlets.get(i);
            Vm vm = vms.get(vmIdx);
            double exec = cl.getLength() / (vm.getMips() * vm.getPesNumber());
            vmFinish[vmIdx] += exec;
            totalEnergy += energyEstimator.estimateEnergy(cl, vm);
            
            double finishTime = vmFinish[vmIdx];
            double deadline = (cl instanceof PrioritizedCloudlet) ? ((PrioritizedCloudlet) cl).getDeadline() : 0;
            if (deadline > 0 && finishTime > deadline) totalViolation += (finishTime - deadline);
        }
        double makespan = Arrays.stream(vmFinish).max().orElse(0.0);
        return wEnergy * totalEnergy + wMakespan * makespan + wViolation * totalViolation * 1000.0;
    }

    private static class Individual {
        int[] assignment;
        double fitness;
        Individual(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
        public Individual copy() {
            return new Individual(assignment.clone(), fitness);
        }
    }
}

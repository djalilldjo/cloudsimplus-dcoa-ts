package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class DWOAMapper {

    private final int population;
    private final int iterations;
    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;
    
    private double wEnergy = 0.2;
    private double wMakespan = 0.7;
    private double wViolation = 0.1;

    public DWOAMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
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

        List<Whale> pod = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            int[] assign = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++) assign[j] = random.nextInt(numVms);
            pod.add(new Whale(assign, fitness(assign, cloudlets, vms)));
        }

        Whale bestPrey = null;

        for (int iter = 0; iter < iterations; iter++) {
            Whale iterBest = Collections.min(pod, Comparator.comparingDouble(w -> w.fitness));
            if (bestPrey == null || iterBest.fitness < bestPrey.fitness) {
                bestPrey = iterBest.copy();
            }

            double a = 2.0 - iter * (2.0 / iterations); // linearly decreases from 2 to 0

            for (int i = 0; i < population; i++) {
                int[] newAssign = new int[numCloudlets];
                Whale current = pod.get(i);
                
                double p = random.nextDouble();
                
                for (int j = 0; j < numCloudlets; j++) {
                    if (p < 0.5) {
                        // Shrinking encircling mechanism
                        double r1 = random.nextDouble();
                        double A = 2 * a * r1 - a;
                        
                        if (Math.abs(A) < 1) {
                            // Exploit: move towards best prey
                            newAssign[j] = (random.nextDouble() < 0.6) ? bestPrey.assignment[j] : current.assignment[j];
                        } else {
                            // Explore: move towards random whale
                            int randWhale = random.nextInt(population);
                            newAssign[j] = (random.nextDouble() < 0.5) ? pod.get(randWhale).assignment[j] : random.nextInt(numVms);
                        }
                    } else {
                        // Spiral updating position (assume best prey is followed loosely)
                        newAssign[j] = (random.nextDouble() < 0.8) ? bestPrey.assignment[j] : current.assignment[j];
                    }
                    
                    // Mutation to avoid stagnation
                    if (random.nextDouble() < 0.05) {
                        newAssign[j] = random.nextInt(numVms);
                    }
                }
                
                double newFit = fitness(newAssign, cloudlets, vms);
                if (newFit < current.fitness) {
                    current.assignment = newAssign;
                    current.fitness = newFit;
                }
            }
        }
        
        return bestPrey.assignment;
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

    private static class Whale {
        int[] assignment;
        double fitness;
        Whale(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
        public Whale copy() {
            return new Whale(assignment.clone(), fitness);
        }
    }
}

package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class ICOAMapper {

    private final int population;
    private final int iterations;
    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;
    
    private double wEnergy = 0.2;
    private double wMakespan = 0.7;
    private double wViolation = 0.1;

    public ICOAMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
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

        List<Coati> pop = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            int[] assign = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++) assign[j] = random.nextInt(numVms);
            pop.add(new Coati(assign, fitness(assign, cloudlets, vms)));
        }

        Coati globalBest = null;

        for (int iter = 0; iter < iterations; iter++) {
            // Find global best (the Iguana)
            Coati iterBest = Collections.min(pop, Comparator.comparingDouble(c -> c.fitness));
            if (globalBest == null || iterBest.fitness < globalBest.fitness) {
                globalBest = iterBest.copy();
            }

            for (int i = 0; i < population; i++) {
                Coati current = pop.get(i);
                int[] newAssign = new int[numCloudlets];
                
                // Phase 1: Hunting and attacking iguana (Exploration)
                for (int j = 0; j < numCloudlets; j++) {
                    if (random.nextDouble() < 0.5) {
                        newAssign[j] = globalBest.assignment[j];
                    } else {
                        // Move randomly in search space
                        newAssign[j] = random.nextInt(numVms);
                    }
                }
                
                double fitP1 = fitness(newAssign, cloudlets, vms);
                if (fitP1 < current.fitness) {
                    current.assignment = newAssign.clone();
                    current.fitness = fitP1;
                }
                
                // Phase 2: Escaping predators (Exploitation)
                int[] p2Assign = new int[numCloudlets];
                for (int j = 0; j < numCloudlets; j++) {
                    if (random.nextDouble() < 0.3) {
                        // Small local perturbation
                        p2Assign[j] = (current.assignment[j] + random.nextInt(3) - 1 + numVms) % numVms;
                    } else {
                        p2Assign[j] = current.assignment[j];
                    }
                }
                
                double fitP2 = fitness(p2Assign, cloudlets, vms);
                if (fitP2 < current.fitness) {
                    current.assignment = p2Assign;
                    current.fitness = fitP2;
                }
                
                // I-COA Improvement Phase: Opposition-Based Learning (OBL) jump
                // Occasionally generate an opposing solution to escape local optima
                if (random.nextDouble() < 0.1) {
                    int[] oblAssign = new int[numCloudlets];
                    for (int j = 0; j < numCloudlets; j++) {
                        oblAssign[j] = (numVms - 1) - current.assignment[j]; // Opposite VM mapping
                    }
                    double oblFit = fitness(oblAssign, cloudlets, vms);
                    if (oblFit < current.fitness) {
                        current.assignment = oblAssign;
                        current.fitness = oblFit;
                    }
                }
            }
        }
        
        // Final update
        Coati finalBest = Collections.min(pop, Comparator.comparingDouble(c -> c.fitness));
        if (finalBest.fitness < globalBest.fitness) {
            globalBest = finalBest;
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

    private static class Coati {
        int[] assignment;
        double fitness;
        Coati(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
        public Coati copy() {
            return new Coati(assignment.clone(), fitness);
        }
    }
}

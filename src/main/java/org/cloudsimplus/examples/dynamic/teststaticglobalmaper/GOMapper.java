package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class GOMapper {

    private final int population;
    private final int iterations;
    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;
    
    private double wEnergy = 0.2;
    private double wMakespan = 0.7;
    private double wViolation = 0.1;

    public GOMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
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

        List<Grasshopper> swarm = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            int[] assign = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++) assign[j] = random.nextInt(numVms);
            swarm.add(new Grasshopper(assign, fitness(assign, cloudlets, vms)));
        }

        Grasshopper targetPhase = null;

        for (int iter = 0; iter < iterations; iter++) {
            // Find global best
            Grasshopper iterBest = Collections.min(swarm, Comparator.comparingDouble(g -> g.fitness));
            if (targetPhase == null || iterBest.fitness < targetPhase.fitness) {
                targetPhase = iterBest.copy();
            }

            double cMax = 1.0;
            double cMin = 0.00004;
            double c = cMax - iter * ((cMax - cMin) / iterations);

            for (int i = 0; i < population; i++) {
                int[] newAssign = new int[numCloudlets];
                Grasshopper current = swarm.get(i);
                
                for (int j = 0; j < numCloudlets; j++) {
                    // Grasshopper move: Combination of moving towards target and interacting with others
                    // Discrete approximation
                    if (random.nextDouble() < c) {
                        // Exploit towards target
                        newAssign[j] = targetPhase.assignment[j];
                    } else {
                        // Explore/Interact
                        if (random.nextDouble() < 0.5) {
                            int randG = random.nextInt(population);
                            newAssign[j] = swarm.get(randG).assignment[j];
                        } else {
                            newAssign[j] = random.nextInt(numVms);
                        }
                    }
                    
                    // Mutation
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
        
        return targetPhase.assignment;
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

    private static class Grasshopper {
        int[] assignment;
        double fitness;
        Grasshopper(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
        public Grasshopper copy() {
            return new Grasshopper(assignment.clone(), fitness);
        }
    }
}

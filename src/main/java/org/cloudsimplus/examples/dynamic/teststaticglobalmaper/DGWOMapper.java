package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

public class DGWOMapper {

    private final int population;
    private final int iterations;
    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;
    
    private double wEnergy = 0.2;
    private double wMakespan = 0.7;
    private double wViolation = 0.1;

    public DGWOMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
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

        List<Wolf> pack = new ArrayList<>();
        for (int i = 0; i < population; i++) {
            int[] assign = new int[numCloudlets];
            for (int j = 0; j < numCloudlets; j++) assign[j] = random.nextInt(numVms);
            pack.add(new Wolf(assign, fitness(assign, cloudlets, vms)));
        }

        Wolf alpha = null, beta = null, delta = null;

        for (int iter = 0; iter < iterations; iter++) {
            // Sort pack to find new alpha, beta, delta
            pack.sort(Comparator.comparingDouble(w -> w.fitness));
            alpha = pack.get(0).copy();
            beta = pack.get(1).copy();
            delta = pack.get(2).copy();

            double a = 2.0 - iter * (2.0 / iterations); // linearly decreases from 2 to 0

            for (int i = 0; i < population; i++) {
                int[] newAssign = new int[numCloudlets];
                Wolf current = pack.get(i);
                
                for (int j = 0; j < numCloudlets; j++) {
                    double r1 = random.nextDouble();
                    double A1 = 2 * a * r1 - a;
                    
                    // Discrete update approximation:
                    // If |A| > 1, explore by random assignment or replacing with random wolf portion
                    if (Math.abs(A1) > 1.0) {
                        newAssign[j] = random.nextInt(numVms);
                    } else {
                        // Exploit: take majority vote or random pick between Alpha, Beta, Delta
                        double choice = random.nextDouble();
                        if (choice < 0.33) newAssign[j] = alpha.assignment[j];
                        else if (choice < 0.66) newAssign[j] = beta.assignment[j];
                        else newAssign[j] = delta.assignment[j];
                    }
                    
                    // Small mutation chance
                    if (random.nextDouble() < 0.05) newAssign[j] = random.nextInt(numVms);
                }
                
                double newFit = fitness(newAssign, cloudlets, vms);
                if (newFit < current.fitness) {
                    current.assignment = newAssign;
                    current.fitness = newFit;
                }
            }
        }
        
        pack.sort(Comparator.comparingDouble(w -> w.fitness));
        return pack.get(0).assignment;
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

    private static class Wolf {
        int[] assignment;
        double fitness;
        Wolf(int[] assignment, double fitness) {
            this.assignment = assignment;
            this.fitness = fitness;
        }
        public Wolf copy() {
            return new Wolf(assignment.clone(), fitness);
        }
    }
}

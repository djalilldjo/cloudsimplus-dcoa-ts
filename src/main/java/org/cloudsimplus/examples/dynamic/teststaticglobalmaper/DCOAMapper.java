package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

public class DCOAMapper {

    private final int population;
    private final int iterations;
    private final Random random;
    private final List<Vm> vmList;
    private final EnergyEstimator energyEstimator;
    private double wEnergy = 0.2;
    private double wMakespan = 0.7;
    private double wViolation = 0.1;

    public DCOAMapper(List<Vm> vmList, int population, int iterations) {
        this(vmList, population, iterations, System.currentTimeMillis(), (cl, vm) -> (cl.getLength() / (vm.getMips() * vm.getPesNumber())) * 100.0);
    }

    public DCOAMapper(List<Vm> vmList, int population, int iterations, long seed, EnergyEstimator energyEstimator) {
        this.vmList = vmList;
        this.population = population;
        this.iterations = iterations;
        this.random = new Random(seed);
        this.energyEstimator = energyEstimator;
    }

    public void setWeights(double e, double m, double v) { this.wEnergy = e; this.wMakespan = m; this.wViolation = v; }

    public int[] findBestMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        int numCloudlets = cloudlets.size();
        int numVms = vms.size();

        List<Coati> coatis = new ArrayList<>();
        
        // GRASP Initialization with true SpaceShared Multi-Processor mathematics
        for (int p = 0; p < this.population; p++) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < numCloudlets; i++) order.add(i);
            
            if (p == 0) {} // Exact Sequential processing
            else if (p == 1) order.sort((a,b) -> Double.compare(cloudlets.get(b).getLength(), cloudlets.get(a).getLength())); // LJF
            else if (p == 2) order.sort((a,b) -> Double.compare(cloudlets.get(a).getLength(), cloudlets.get(b).getLength())); // SJF
            else Collections.shuffle(order, random);
            
            int[] assign = new int[numCloudlets];
            double[] vmWorkload = new double[numVms];
            double[] maxTaskTime = new double[numVms];
            
            for (int i : order) {
                Cloudlet cl = cloudlets.get(i);
                int bestVm = 0;
                double bestFit = Double.MAX_VALUE;
                double bestTaskTime = 0;

                for (int v = 0; v < numVms; v++) {
                    Vm vm = vms.get(v);
                    double taskTime = cl.getLength() / vm.getMips(); // Time on a single PE
                    double projectedFinish = Math.max(Math.max(maxTaskTime[v], taskTime), (vmWorkload[v] + taskTime) / vm.getPesNumber());
                    
                    double fit = this.wEnergy * energyEstimator.estimateEnergy(cl, vm) + this.wMakespan * projectedFinish * 100.0;
                    if (fit < bestFit) { bestFit = fit; bestVm = v; bestTaskTime = taskTime; }
                }
                assign[i] = bestVm;
                vmWorkload[bestVm] += bestTaskTime;
                if (bestTaskTime > maxTaskTime[bestVm]) maxTaskTime[bestVm] = bestTaskTime;
            }
            coatis.add(new Coati(assign, fitness(assign, cloudlets, vms)));
        }

        Coati globalBest = coatis.stream().min(Comparator.comparingDouble(c -> c.fitness)).get().copy();

        for (int iter = 0; iter < this.iterations; iter++) {
            coatis.sort(Comparator.comparingDouble(c -> c.fitness));
            if (coatis.get(0).fitness < globalBest.fitness) globalBest = coatis.get(0).copy();
            int[] best = globalBest.assignment.clone();

            for (int i = 1; i < this.population / 2; i++) { 
                int[] newAssign = coatis.get(i).assignment.clone();
                for (int j = 0; j < numCloudlets; j++) if (random.nextDouble() < 0.7) newAssign[j] = best[j]; 
                double newFit = fitness(newAssign, cloudlets, vms);
                if (newFit < coatis.get(i).fitness) { coatis.get(i).assignment = newAssign; coatis.get(i).fitness = newFit; }
            }

            for (int i = this.population / 2; i < this.population; i++) {
                int[] newAssign = coatis.get(i).assignment.clone();
                int idxToChange = random.nextInt(numCloudlets);
                newAssign[idxToChange] = random.nextInt(numVms);
                double newFit = fitness(newAssign, cloudlets, vms);
                if (newFit < coatis.get(i).fitness) { coatis.get(i).assignment = newAssign; coatis.get(i).fitness = newFit; }
            }

            for (int i = 1; i < this.population; i++) {
                int[] newAssign = coatis.get(i).assignment.clone();
                
                if (random.nextDouble() < 0.05) {
                    int jumps = Math.max(1, (int) (numCloudlets * Math.pow(random.nextDouble(), -0.5) % (numCloudlets / 2.0)));
                    for(int k=0; k<jumps; k++) newAssign[random.nextInt(numCloudlets)] = random.nextInt(numVms);
                } else {
                    int idx1 = random.nextInt(numCloudlets);
                    int idx2 = random.nextInt(numCloudlets);
                    int temp = newAssign[idx1];
                    newAssign[idx1] = newAssign[idx2];
                    newAssign[idx2] = temp;
                }
                
                double newFit = fitness(newAssign, cloudlets, vms);
                if (newFit < coatis.get(i).fitness) { coatis.get(i).assignment = newAssign; coatis.get(i).fitness = newFit; }
            }
            coatis.get(0).assignment = globalBest.assignment.clone();
            coatis.get(0).fitness = globalBest.fitness;
        }
        return globalBest.assignment;
    }

    private double fitness(int[] assignment, List<Cloudlet> cloudlets, List<Vm> vms) {
        int numVms = vms.size();
        double[] vmWorkload = new double[numVms];
        double[] maxTaskTime = new double[numVms];
        double totalEnergy = 0, totalViolation = 0;

        for (int i = 0; i < assignment.length; i++) {
            int vmIdx = assignment[i];
            Cloudlet cl = cloudlets.get(i);
            Vm vm = vms.get(vmIdx);
            
            double taskTime = cl.getLength() / vm.getMips(); 
            vmWorkload[vmIdx] += taskTime;
            if (taskTime > maxTaskTime[vmIdx]) maxTaskTime[vmIdx] = taskTime;
            
            totalEnergy += energyEstimator.estimateEnergy(cl, vm);
            
            double deadline = (cl instanceof PrioritizedCloudlet) ? ((PrioritizedCloudlet) cl).getDeadline() : 0;
            if (deadline > 0 && taskTime > deadline) totalViolation += (taskTime - deadline);
        }

        double makespan = 0;
        for (int v = 0; v < numVms; v++) {
            double vmFinish = Math.max(maxTaskTime[v], vmWorkload[v] / vms.get(v).getPesNumber());
            if (vmFinish > makespan) makespan = vmFinish;
        }

        // Scale makespan to equal magnitude of energy for optimal balance (100x scale yields powerful Makespan scheduling)
        return wEnergy * totalEnergy + wMakespan * (makespan * 100.0) + wViolation * totalViolation * 1000.0;
    }

    private static class Coati {
        int[] assignment; double fitness;
        Coati(int[] assignment, double fitness) { this.assignment = assignment; this.fitness = fitness; }
        public Coati copy() { return new Coati(assignment.clone(), fitness); }
    }
}

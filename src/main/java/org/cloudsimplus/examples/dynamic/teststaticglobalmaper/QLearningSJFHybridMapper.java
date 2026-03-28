package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

/**
 * Hybrid static mapper: uses SJF for short cloudlets, Q-learning for outlier (large) cloudlets.
 */
public class QLearningSJFHybridMapper {

    private final List<Vm> vmList;
    private final int numVms;
    private final int episodes;
    private final double alpha, gamma, epsilon, minEpsilon, epsilonDecay;
    private double[][] qTable;

    // Fraction of cloudlets using SJF (rest use Q-learning)
    private final double SJF_FRACTION = 0.8;

    public QLearningSJFHybridMapper(List<Vm> vmList) {
        this(vmList, 1000, 0.1, 0.9, 1.0, 0.01, 0.995); // You can tune episodes and rates as needed
    }
    public QLearningSJFHybridMapper(List<Vm> vmList, int episodes, double alpha, double gamma, double epsilon, double minEpsilon, double epsilonDecay) {
        this.vmList = vmList;
        this.numVms = vmList.size();
        this.episodes = episodes;
        this.alpha = alpha; this.gamma = gamma;
        this.epsilon = epsilon; this.minEpsilon = minEpsilon; this.epsilonDecay = epsilonDecay;
    }

    /**
     * Hybrid mapping: SJF for most cloudlets, Q-learning for hardest ones.
     */
    public void map(List<Cloudlet> cloudlets) {
        int numCloudlets = cloudlets.size();
        // Sort by cloudlet length ascending for SJF
        List<Cloudlet> sorted = new ArrayList<>(cloudlets);
        sorted.sort(Comparator.comparingLong(Cloudlet::getLength));

        int splitIdx = (int)Math.round(SJF_FRACTION * numCloudlets); // SJF for this many cloudlets
        // --- 1. SJF assignment for shortest jobs
        for (int i = 0; i < splitIdx; i++) {
            Cloudlet cl = sorted.get(i);
            cl.setVm(vmList.get(i % numVms));   // SJF round-robin across VMs
        }

        // --- 2. Q-learning for remaining (largest) jobs
        List<Cloudlet> remainder = sorted.subList(splitIdx, numCloudlets);
        int nRem = remainder.size();
        this.qTable = new double[nRem][numVms];
        Random rand = new Random();
        double eps = epsilon;

        for (int ep = 0; ep < episodes; ep++) {
            for (int cIdx = 0; cIdx < nRem; cIdx++) {
                Cloudlet cloudlet = remainder.get(cIdx);
                int actionVm;
                if (rand.nextDouble() < eps) {
                    actionVm = rand.nextInt(numVms);
                } else {
                    actionVm = bestAction(qTable[cIdx]);
                }
                double reward = -fitness(vmList.get(actionVm), cloudlet);
                double oldQ = qTable[cIdx][actionVm];
                double maxQ = Arrays.stream(qTable[cIdx]).max().orElse(0.0);
                qTable[cIdx][actionVm] = oldQ + alpha * (reward + gamma * maxQ - oldQ);
            }
            if (eps > minEpsilon) eps *= epsilonDecay;
        }

        // Assign remainder based on learned policy
        for (int cIdx = 0; cIdx < nRem; cIdx++) {
            int bestVm = bestAction(qTable[cIdx]);
            remainder.get(cIdx).setVm(vmList.get(bestVm));
        }
    }

    private int bestAction(double[] qValues) {
        int best = 0;
        for (int i = 1; i < qValues.length; i++) if (qValues[i] > qValues[best]) best = i;
        return best;
    }

    /**
     * Fitness: energy + penalty for deadline violation (if enabled).
     * You can customize this to be more sophisticated.
     */
    private double fitness(Vm vm, Cloudlet cloudlet) {
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
        double finishTime = execTime;
        // Allow for deadline penalties if PrioritizedCloudlet
        double deadline = (cloudlet instanceof PrioritizedCloudlet) ? ((PrioritizedCloudlet) cloudlet).getDeadline() : 0;
        double violationPenalty = (deadline > 0 && finishTime > deadline) ? (finishTime - deadline) : 0;
        if (vm.getHost() == null || vm.getHost().getPowerModel() == null) {
            return execTime + violationPenalty * 1000;
        }
        var host = vm.getHost();
        var powerModel = host.getPowerModel();
        double currentUtil = host.getCpuUtilizationStats().getMean();
        double vmUtilIncrease = (double) cloudlet.getPesNumber() * vm.getMips() / host.getTotalMipsCapacity();
        double projectedUtil = Math.min(1.0, currentUtil + vmUtilIncrease);
        double avgPower = powerModel.getPower((currentUtil + projectedUtil) / 2.0);
        double energy = avgPower * execTime;
        double alpha = 1.0, beta = 1000.0;
        return alpha * energy + beta * violationPenalty;
    }
}

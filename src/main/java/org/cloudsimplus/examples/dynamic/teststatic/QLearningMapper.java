package org.cloudsimplus.examples.dynamic.teststatic;


import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Static Q-Learning Mapper for assigning cloudlets to VMs before simulation.
 * Each cloudlet is mapped to a VM based on Q-learning, optimizing for energy and deadline.
 */
public class QLearningMapper {
    private final List<Vm> vmList;
    private final int numVms;
    private final int episodes;
    private final double alpha;   // Learning rate
    private final double gamma;   // Discount factor
    private final double epsilon; // Exploration rate
    private final double minEpsilon;
    private final double epsilonDecay;

    // Q-table: state (cloudlet index) x action (VM index)
    private double[][] qTable;

    public QLearningMapper(List<Vm> vmList) {
        this(vmList, 100, 0.1, 0.9, 1.0, 0.01, 0.995);
    }

    public QLearningMapper(List<Vm> vmList, int episodes, double alpha, double gamma, double epsilon, double minEpsilon, double epsilonDecay) {
        this.vmList = vmList;
        this.numVms = vmList.size();
        this.episodes = episodes;
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.minEpsilon = minEpsilon;
        this.epsilonDecay = epsilonDecay;
    }

    /**
     * Runs Q-learning to assign each cloudlet to a VM.
     */
    public void map(List<Cloudlet> cloudlets) {
        int numCloudlets = cloudlets.size();
        qTable = new double[numCloudlets][numVms];
        Random rand = new Random();

        // Q-learning episodes
        double eps = epsilon;
        for (int ep = 0; ep < episodes; ep++) {
            for (int cIdx = 0; cIdx < numCloudlets; cIdx++) {
                Cloudlet cloudlet = cloudlets.get(cIdx);

                // Choose action: epsilon-greedy
                int actionVm;
                if (rand.nextDouble() < eps) {
                    actionVm = rand.nextInt(numVms); // Explore
                } else {
                    actionVm = bestAction(qTable[cIdx]);
                }

                // Calculate reward for assigning cloudlet to VM
                double reward = -fitness(vmList.get(actionVm), cloudlet);

                // Q-learning update: Q(s,a) = Q(s,a) + alpha * (reward + gamma * max_a' Q(s',a') - Q(s,a))
                double oldQ = qTable[cIdx][actionVm];
                double maxQ = Arrays.stream(qTable[cIdx]).max().orElse(0.0);
                qTable[cIdx][actionVm] = oldQ + alpha * (reward + gamma * maxQ - oldQ);
            }
            // Decay epsilon
            if (eps > minEpsilon) eps *= epsilonDecay;
        }

        // After learning, assign each cloudlet to the VM with the best Q-value
        for (int cIdx = 0; cIdx < numCloudlets; cIdx++) {
            int bestVm = bestAction(qTable[cIdx]);
            cloudlets.get(cIdx).setVm(vmList.get(bestVm));
        }
    }

    private int bestAction(double[] qValues) {
        int best = 0;
        for (int i = 1; i < qValues.length; i++) {
            if (qValues[i] > qValues[best]) best = i;
        }
        return best;
    }

    /**
     * Fitness: energy + deadline violation penalty (lower is better)
     */
    private double fitness(Vm vm, Cloudlet cloudlet) {
        double execTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
        double finishTime = execTime;
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

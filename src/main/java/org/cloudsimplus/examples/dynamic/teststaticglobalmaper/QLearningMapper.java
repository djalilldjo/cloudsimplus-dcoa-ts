package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;
import java.util.*;

/**
 * QLearningMapper with makespan minimization, suitable for static VM assignment.
 */
public class QLearningMapper {

    private final List<Vm> vmList;
    private final int numVms;
    private final int episodes;
    private final double alpha; // Learning rate
    private final double gamma; // Discount factor
    private final double epsilon; // Exploration rate
    private final double minEpsilon;
    private final double epsilonDecay;
    private final Random rand;
    private final EnergyEstimator energyEstimator;
    private double[][] qTable;

    // Increase episodes for better learning on large problems!
    public QLearningMapper(List<Vm> vmList, long seed, EnergyEstimator energyEstimator) {
        this(vmList, 2000, 0.1, 0.9, 1.0, 0.01, 0.995, seed, energyEstimator);
    }

    public QLearningMapper(List<Vm> vmList, int episodes, double alpha, double gamma, double epsilon, double minEpsilon, double epsilonDecay, long seed, EnergyEstimator energyEstimator) {
        this.vmList = vmList;
        this.numVms = vmList.size();
        this.episodes = episodes;
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.minEpsilon = minEpsilon;
        this.epsilonDecay = epsilonDecay;
        this.rand = new Random(seed);
        this.energyEstimator = energyEstimator;
    }

    /** Assigns each cloudlet to a VM using Q-learning to optimize makespan + energy. */
    public void map(List<Cloudlet> cloudlets) {
        int numCloudlets = cloudlets.size();
        qTable = new double[numCloudlets][numVms];

        // For per-episode projected VM finish workload tracking
        int[] bestActions = new int[numCloudlets];

        double eps = epsilon;
        for (int ep = 0; ep < episodes; ep++) {
            // For each episode, track projected finish time for all VMs
            double[] vmFinish = new double[numVms];

            for (int cIdx = 0; cIdx < numCloudlets; cIdx++) {
                Cloudlet cloudlet = cloudlets.get(cIdx);

                // Epsilon-greedy VM selection
                int actionVm = (rand.nextDouble() < eps) ? rand.nextInt(numVms) : bestAction(qTable[cIdx]);
                // Simulate assignment and compute reward
                double projectedFinish = vmFinish[actionVm] + getExecTime(cloudlet, vmList.get(actionVm));
                double reward = -fitness(vmList.get(actionVm), cloudlet, projectedFinish); // negative for min

                double oldQ = qTable[cIdx][actionVm];
                double maxQ = Arrays.stream(qTable[cIdx]).max().orElse(0.0);
                qTable[cIdx][actionVm] = oldQ + alpha * (reward + gamma * maxQ - oldQ);

                // Update projected finish for this VM (for next jobs in this episode)
                vmFinish[actionVm] = projectedFinish;

                bestActions[cIdx] = actionVm;
            }
            if (eps > minEpsilon) eps *= epsilonDecay;
        }

        // After learning, assign each cloudlet to VM with best Q-value
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
     * Fitness = weighted combo of energy and projected makespan.
     *
     * @param vm The VM to assign the cloudlet to.
     * @param cloudlet The cloudlet being mapped.
     * @param projectedFinish The VM's new finish time if this mapping is done.
     * @return Lower = better.
     */
    private double fitness(Vm vm, Cloudlet cloudlet, double projectedFinish) {
        // Estimate energy consumption
        double energy = energyEstimator.estimateEnergy(cloudlet, vm);

        // Deadline violation (if using deadlines)
        double violationPenalty = 0;
        double deadline = (cloudlet instanceof PrioritizedCloudlet) ? ((PrioritizedCloudlet) cloudlet).getDeadline() : 0;
        if (deadline > 0 && projectedFinish > deadline)
            violationPenalty = (projectedFinish - deadline);

        // Weights: adjust as needed! (e.g., wMakespan=0.7 for makespan priority)
        double wEnergy = 0.2;
        double wMakespan = 0.7;
        double wViolation = 0.1;

        return wEnergy * energy + wMakespan * projectedFinish + wViolation * violationPenalty * 1000.0;
    }

    private double getExecTime(Cloudlet cloudlet, Vm vm) {
        return cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
    }
}

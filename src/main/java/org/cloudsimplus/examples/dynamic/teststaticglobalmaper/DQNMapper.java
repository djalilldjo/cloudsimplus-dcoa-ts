package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * DQNMapper using a pure-Java Deep Q-Network.
 * Upgrades the Tabular Q-Learning to Deep RL with Replay Buffer and Target Networks.
 */
public class DQNMapper {

    private final List<Vm> vmList;
    private final int numVms;
    private final int episodes;
    private final double gamma;
    private final double minEpsilon;
    private final double epsilonDecay;
    private final Random rand;
    private final EnergyEstimator energyEstimator;
    
    private final NeuralNet policyNet;
    private final NeuralNet targetNet;
    private final ReplayBuffer replayBuffer;
    private final int batchSize = 32;
    private final int targetUpdateFreq = 10;

    public DQNMapper(List<Vm> vmList, int episodes, double alpha, double gamma, double epsilon, double minEpsilon, double epsilonDecay, long seed, EnergyEstimator energyEstimator) {
        this.vmList = vmList;
        this.numVms = vmList.size();
        this.episodes = episodes;
        this.gamma = gamma;
        this.minEpsilon = minEpsilon;
        this.epsilonDecay = epsilonDecay;
        this.rand = new Random(seed);
        this.energyEstimator = energyEstimator;

        int stateSize = 1 + numVms; // [Cloudlet Length, VM1_Load, VM2_Load, ...]
        int actionSize = numVms;
        
        // Simple 2-layer MLP: State -> 64 -> Action Q-values
        this.policyNet = new NeuralNet(stateSize, 64, actionSize, alpha, rand);
        this.targetNet = new NeuralNet(stateSize, 64, actionSize, alpha, rand);
        this.targetNet.copyWeightsFrom(this.policyNet);
        
        this.replayBuffer = new ReplayBuffer(2000, rand);
    }

    public void map(List<Cloudlet> cloudlets) {
        int numCloudlets = cloudlets.size();
        double eps = 1.0; // Start with full exploration

        int[] bestActions = new int[numCloudlets];

        for (int ep = 0; ep < episodes; ep++) {
            double[] vmLoads = new double[numVms];

            for (int cIdx = 0; cIdx < numCloudlets; cIdx++) {
                Cloudlet cloudlet = cloudlets.get(cIdx);
                double[] state = buildState(cloudlet, vmLoads);

                // Epsilon-greedy action
                int actionVm;
                if (rand.nextDouble() < eps) {
                    actionVm = rand.nextInt(numVms);
                } else {
                    double[] qValues = policyNet.predict(state);
                    actionVm = argMax(qValues);
                }

                // Simulate environment step
                double execTime = getExecTime(cloudlet, vmList.get(actionVm));
                double projectedFinish = vmLoads[actionVm] + execTime;
                double reward = -fitness(vmList.get(actionVm), cloudlet, projectedFinish); 
                
                vmLoads[actionVm] = projectedFinish;
                
                double[] nextState = null;
                if (cIdx < numCloudlets - 1) {
                    nextState = buildState(cloudlets.get(cIdx + 1), vmLoads);
                }

                // Store transition
                replayBuffer.add(new Transition(state, actionVm, reward, nextState));
                
                // Track best actions for the final iteration
                if (ep == episodes - 1) {
                    bestActions[cIdx] = actionVm;
                }

                // Train network
                if (replayBuffer.size() >= batchSize) {
                    trainBatch();
                }
            }
            
            // Update Target Network
            if (ep % targetUpdateFreq == 0) {
                targetNet.copyWeightsFrom(policyNet);
            }

            if (eps > minEpsilon) {
                eps *= epsilonDecay;
            }
        }

        // Apply best known actions from the final fully-trained episode traversal
        for (int cIdx = 0; cIdx < numCloudlets; cIdx++) {
            cloudlets.get(cIdx).setVm(vmList.get(bestActions[cIdx]));
        }
    }

    private void trainBatch() {
        List<Transition> batch = replayBuffer.sample(batchSize);
        for (Transition t : batch) {
            double[] currentQ = policyNet.predict(t.state);
            double targetQ = t.reward;
            if (t.nextState != null) {
                double[] nextQ = targetNet.predict(t.nextState);
                targetQ += gamma * Arrays.stream(nextQ).max().orElse(0.0);
            }
            
            currentQ[t.action] = targetQ; // Update target for the taken action
            policyNet.trainStep(t.state, currentQ);
        }
    }

    private double[] buildState(Cloudlet cloudlet, double[] vmLoads) {
        double[] state = new double[1 + numVms];
        // Normalize length roughly (assuming millions of MI) to keep gradients stable
        state[0] = cloudlet.getLength() / 100000.0; 
        for (int i = 0; i < numVms; i++) {
            state[i + 1] = vmLoads[i] / 1000.0; // Rough normalization for projected finish times
        }
        return state;
    }

    private int argMax(double[] array) {
        int best = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[best]) best = i;
        }
        return best;
    }

    private double fitness(Vm vm, Cloudlet cloudlet, double projectedFinish) {
        double energy = energyEstimator.estimateEnergy(cloudlet, vm);
        double violationPenalty = 0;
        double deadline = (cloudlet instanceof PrioritizedCloudlet) ? ((PrioritizedCloudlet) cloudlet).getDeadline() : 0;
        if (deadline > 0 && projectedFinish > deadline) {
            violationPenalty = (projectedFinish - deadline);
        }
        double wEnergy = 0.2, wMakespan = 0.7, wViolation = 0.1;
        // Normalize feedback appropriately for stable DQN training
        return (wEnergy * energy + wMakespan * projectedFinish + wViolation * violationPenalty * 1000.0) / 10000.0;
    }

    private double getExecTime(Cloudlet cloudlet, Vm vm) {
        return cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
    }

    // --- Pure Java MLP Neural Network ---
    private static class NeuralNet {
        int inputSize, hiddenSize, outputSize;
        double learningRate;
        double[][] W1, W2;
        double[] b1, b2;

        public NeuralNet(int input, int hidden, int output, double lr, Random rand) {
            this.inputSize = input; this.hiddenSize = hidden; this.outputSize = output;
            this.learningRate = lr;
            W1 = new double[input][hidden]; b1 = new double[hidden];
            W2 = new double[hidden][output]; b2 = new double[output];
            
            // He Initialization
            for(int i=0; i<input; i++) for(int j=0; j<hidden; j++) W1[i][j] = rand.nextGaussian() * Math.sqrt(2.0/input);
            for(int i=0; i<hidden; i++) for(int j=0; j<output; j++) W2[i][j] = rand.nextGaussian() * Math.sqrt(2.0/hidden);
        }

        public void copyWeightsFrom(NeuralNet other) {
            for(int i=0; i<inputSize; i++) System.arraycopy(other.W1[i], 0, W1[i], 0, hiddenSize);
            System.arraycopy(other.b1, 0, b1, 0, hiddenSize);
            for(int i=0; i<hiddenSize; i++) System.arraycopy(other.W2[i], 0, W2[i], 0, outputSize);
            System.arraycopy(other.b2, 0, b2, 0, outputSize);
        }

        public double[] predict(double[] input) {
            double[] h = new double[hiddenSize];
            for (int j = 0; j < hiddenSize; j++) {
                double sum = b1[j];
                for (int i = 0; i < inputSize; i++) sum += input[i] * W1[i][j];
                h[j] = Math.max(0, sum); // ReLU
            }
            double[] out = new double[outputSize];
            for (int j = 0; j < outputSize; j++) {
                double sum = b2[j];
                for (int i = 0; i < hiddenSize; i++) sum += h[i] * W2[i][j];
                out[j] = sum; // Linear output
            }
            return out;
        }

        public void trainStep(double[] input, double[] targetQ) {
            // Forward pass (caching hidden layer)
            double[] h = new double[hiddenSize];
            double[] hRaw = new double[hiddenSize];
            for (int j = 0; j < hiddenSize; j++) {
                double sum = b1[j];
                for (int i = 0; i < inputSize; i++) sum += input[i] * W1[i][j];
                hRaw[j] = sum;
                h[j] = Math.max(0, sum); 
            }
            double[] out = new double[outputSize];
            for (int j = 0; j < outputSize; j++) {
                double sum = b2[j];
                for (int i = 0; i < hiddenSize; i++) sum += h[i] * W2[i][j];
                out[j] = sum;
            }

            // Backprop MSE
            double[] dOut = new double[outputSize];
            for(int j=0; j<outputSize; j++) dOut[j] = (out[j] - targetQ[j]); 

            double[] dHidden = new double[hiddenSize];
            for(int i=0; i<hiddenSize; i++) {
                double sum = 0;
                for(int j=0; j<outputSize; j++) {
                    sum += dOut[j] * W2[i][j];
                    W2[i][j] -= learningRate * dOut[j] * h[i]; // Update W2
                }
                dHidden[i] = hRaw[i] > 0 ? sum : 0; // ReLU derivative
            }
            
            for(int j=0; j<outputSize; j++) b2[j] -= learningRate * dOut[j];

            for(int i=0; i<inputSize; i++) {
                for(int j=0; j<hiddenSize; j++) {
                    W1[i][j] -= learningRate * dHidden[j] * input[i]; // Update W1
                }
            }
            for(int j=0; j<hiddenSize; j++) b1[j] -= learningRate * dHidden[j];
        }
    }

    private static class Transition {
        double[] state; int action; double reward; double[] nextState;
        public Transition(double[] s, int a, double r, double[] ns) {
            this.state = s; this.action = a; this.reward = r; this.nextState = ns;
        }
    }

    private static class ReplayBuffer {
        private final Transition[] buffer;
        private int ptr = 0;
        private int count = 0;
        private final Random rand;
        
        public ReplayBuffer(int capacity, Random rand) {
            buffer = new Transition[capacity];
            this.rand = rand;
        }
        public void add(Transition t) {
            buffer[ptr] = t;
            ptr = (ptr + 1) % buffer.length;
            if (count < buffer.length) count++;
        }
        public int size() { return count; }
        
        public List<Transition> sample(int batchSize) {
            List<Transition> sample = new ArrayList<>(batchSize);
            for(int i=0; i<batchSize; i++) {
                sample.add(buffer[rand.nextInt(count)]);
            }
            return sample;
        }
    }
}

package org.cloudsimplus.examples;

import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.listeners.CloudletVmEventInfo;

import java.util.*;

/**
 * An example demonstrating Cloudlet scheduling using Q-Learning in CloudSim Plus.
 * This example uses a custom broker (QLearningBroker) that implements Q-Learning
 * to decide which VM a Cloudlet should be mapped to.
 */
public class QLearningSchedulingExample {
    private static final int HOSTS = 4;
    private static final int HOST_PES = 16; // Increased to support more VMs
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 16384; // Increased to 16GB
    private static final long HOST_BW = 20_000; // Increased to 20Gbps
    private static final long HOST_STORAGE = 1_000_000;

    private static final int VMS = 8;
    private static final int VM_PES = 2;
    
    private static final int CLOUDLETS = 10;
    private static final int CLOUDLET_PES = 1; // Reduced to lessen resource demand
    private static final int CLOUDLET_LENGTH = 2000; // Reduced for faster completion
	
    public static void main(String[] args) {
        new QLearningSchedulingExample().run();
    }

    public void run() {
        CloudSimPlus simulation = new CloudSimPlus();

        // Create a datacenter for the simulation
        Datacenter dc = createDatacenter(simulation);
        // Set a small scheduling interval to capture frequent events
        dc.setSchedulingInterval(0.001);

        // Create the custom Q-Learning broker
        QLearningBroker broker = new QLearningBroker(simulation);
        // Set a longer VM destruction delay to allow cloudlets to finish
        broker.setVmDestructionDelay(50.0);
        // Set Q-Learning parameters
        broker.setExplorationRate(0.5) // Increased for better load balancing
              .setLearningRate(0.2)
              .setDiscountFactor(0.9);

        // Submit VMs and Cloudlets to the broker
        broker.submitVmList(createVms(VMS));
        broker.submitCloudletList(createCloudlets(CLOUDLETS, broker));
        simulation.terminateAt(2000.0);
        // Start the simulation
        simulation.start();

        // Build and display the results table for finished Cloudlets
        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();

        System.out.println("Simulation finished!");
    }

    private Datacenter createDatacenter(CloudSimPlus sim) {
        final var hostList = new ArrayList<Host>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }
        // Uses a VmAllocationPolicySimple by default to allocate VMs
        return new DatacenterSimple(sim, hostList);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    private List<Vm> createVms(int count) {
        List<Vm> vms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            vms.add(new VmSimple(1000, VM_PES)
                .setRam(4096) // Increased RAM to 4GB per VM
                .setBw(10000) // Increased bandwidth to 10Gbps
                .setSize(10_000));
        }
        return vms;
    }

    private List<Cloudlet> createCloudlets(int count, QLearningBroker broker) {

        //UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);
        
    	List<Cloudlet> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            //CloudletSimple cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, new UtilizationModelFull());
        	CloudletSimple cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setSizes(256); // Reduced size to lower resource demand
            cloudlet.addOnFinishListener((CloudletVmEventInfo evt) -> {
                final Cloudlet finishedCloudlet = evt.getCloudlet();
                broker.updateQTableForFinishedCloudlet(finishedCloudlet);
            });
            list.add(cloudlet);
        }
        return list;
    }
}

class QLearningBroker extends DatacenterBrokerSimple {
    private final QTable qTable = new QTable();
    private double epsilon = 0.1;
    private double alpha = 0.1;
    private double gamma = 0.9;

    private final Map<Cloudlet, Integer> lastState = new HashMap<>();
    private final Map<Cloudlet, Integer> lastAction = new HashMap<>();

    public QLearningBroker(CloudSimPlus sim) {
        super(sim);
    }

    public QLearningBroker setExplorationRate(double eps) {
        this.epsilon = eps;
        return this;
    }

    public QLearningBroker setLearningRate(double lr) {
        this.alpha = lr;
        return this;
    }

    public QLearningBroker setDiscountFactor(double df) {
        this.gamma = df;
        return this;
    }

    @Override
    protected Vm defaultVmMapper(final Cloudlet cloudlet) {
        final List<Vm> vmList = getVmExecList();
        if (vmList.isEmpty()) {
            throw new IllegalStateException("No VMs available to map cloudlets.");
        }

        final int state = observeState();
        int action;

        if (Math.random() < epsilon) {
            action = new Random().nextInt(vmList.size());
        } else {
            action = qTable.getBestAction(state);
            if (action >= vmList.size()) {
                action = new Random().nextInt(vmList.size());
            }
        }

        // Enhanced load balancing considering CPU and waiting cloudlets
        Vm selectedVm = vmList.get(action);
        long waitingCloudlets = selectedVm.getCloudletScheduler().getCloudletWaitingList().size();
        if (selectedVm.getCpuPercentUtilization() > 0.4 || waitingCloudlets > 2) {
            Optional<Vm> lessLoadedVm = vmList.stream()
                .filter(vm -> vm.getCpuPercentUtilization() < 0.3 && vm.getCloudletScheduler().getCloudletWaitingList().size() < 2)
                .findFirst();
            if (lessLoadedVm.isPresent()) {
                action = vmList.indexOf(lessLoadedVm.get());
            }
        }

        lastState.put(cloudlet, state);
        lastAction.put(cloudlet, action);
        return vmList.get(action);
    }

    public void updateQTableForFinishedCloudlet(final Cloudlet finishedCloudlet) {
        final Integer oldState = lastState.remove(finishedCloudlet);
        final Integer action = lastAction.remove(finishedCloudlet);

        if (oldState == null || action == null) {
            System.err.println("Warning: Finished Cloudlet " + finishedCloudlet.getId() + " did not have a recorded oldState or action. Skipping Q-table update.");
            return;
        }

        final int newState = observeState();
        final double reward = calculateReward(finishedCloudlet);
        final double oldQ = qTable.getQValue(oldState, action);
        final double maxQ = qTable.getMaxQValue(newState);
        final double updatedQ = oldQ + alpha * (reward + gamma * maxQ - oldQ);
        qTable.update(oldState, action, updatedQ);
    }

    private int observeState() {
        double avgUtil = getVmExecList().stream()
            .mapToDouble(Vm::getCpuPercentUtilization)
            .average().orElse(0.0);

        int waiting = (int) getVmExecList().stream()
            .flatMap(vm -> vm.getCloudletScheduler().getCloudletWaitingList().stream())
            .count();

        int utilBin = (int)(avgUtil / 0.2);
        if (utilBin > 4) utilBin = 4;

        int queueBin = Math.min(waiting / 5, 4);
        return utilBin * 5 + queueBin;
    }

    private double calculateReward(Cloudlet cl) {
        double power = getVmExecList().stream()
            .mapToDouble(vm -> {
                double utilization = vm.getCpuUtilizationStats().getMean();
                return vm.getHost().getPowerModel() != null ? vm.getHost().getPowerModel().getPower(utilization) : 0.0;
            })
            .sum();

        double expectedDuration = cl.getLength() / (cl.getPesNumber() * cl.getVm().getMips());
        double actualDuration = cl.getFinishTime() - cl.getSubmissionDelay();
        double slaPenalty = (actualDuration > expectedDuration * 1.5) ? 10.0 : 0.0;

        return -power - slaPenalty;
    }
}

class QTable {
    private final Map<Integer, double[]> table = new HashMap<>();
    private static final int MAX_ACTIONS = 10;

    int getBestAction(int state) {
        double[] qValues = table.computeIfAbsent(state, k -> new double[MAX_ACTIONS]);
        int bestAction = 0;
        for (int i = 1; i < qValues.length; i++) {
            if (qValues[i] > qValues[bestAction]) {
                bestAction = i;
            }
        }
        return bestAction;
    }

    double getMaxQValue(int state) {
        return Arrays.stream(table.computeIfAbsent(state, k -> new double[MAX_ACTIONS]))
                     .max().orElse(0.0);
    }

    double getQValue(int state, int action) {
        if (action < 0 || action >= MAX_ACTIONS) {
            System.err.println("Warning: QTable.getQValue called with out-of-bounds action: " + action + " for state: " + state);
            return 0.0;
        }
        return table.computeIfAbsent(state, k -> new double[MAX_ACTIONS])[action];
    }

    void update(int state, int action, double value) {
        if (action < 0 || action >= MAX_ACTIONS) {
            System.err.println("Error: QTable.update called with out-of-bounds action: " + action + " for state: " + state + ". Update skipped.");
            return;
        }
        table.computeIfAbsent(state, k -> new double[MAX_ACTIONS])[action] = value;
    }
}

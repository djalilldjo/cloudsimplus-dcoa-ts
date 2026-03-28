package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletExecution;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.listeners.EventInfo;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.HostResourceStats;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmResourceStats;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingLong;

/**
 * An example demonstrating Cloudlet scheduling using Q-Learning in CloudSim Plus
 * with dynamic Cloudlet arrival, energy consumption calculation, and dynamic cloudlet lengths.
 */
public class QLearningSchedulingExample {
    private static final int HOSTS = 4;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 2000;
    private static final int HOST_RAM = 16384;
    private static final long HOST_BW = 100000000L;
    private static final long HOST_STORAGE = 1000000;

    private static final double HOST_START_UP_DELAY = 5;
    private static final double HOST_SHUT_DOWN_DELAY = 3;
    private static final double HOST_START_UP_POWER = 5;
    private static final double HOST_SHUT_DOWN_POWER = 3;

    private static final double STATIC_POWER = 35;
    private static final int MAX_POWER = 50;

    private static final int VMS = 16;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;

    private static final int INITIAL_CLOUDLETS = 10;
    private static final int DYNAMIC_CLOUDLETS_TO_CREATE = 200;
    private static final int CLOUDLET_PES = 2;

    // ADDED: List of dynamic cloudlet lengths and a Random instance
    private static final List<Long> CLOUDLET_LENGTHS =
        Arrays.asList(1000L, 1500L, 2000L, 2500L, 3000L, 3500L, 4000L, 4500L, 5000L);
    private final Random random = new Random();

    private static final double CLOUDLET_DEADLINE_FACTOR = 3.0;

    private final CloudSimPlus simulation;
    private final QLearningBroker broker;
    private final List<Cloudlet> allCloudlets;
    private int dynamicCloudletsCreatedCount = 0;

    private final List<Host> hostList;
    private List<Vm> vmList;

    public static void main(String[] args) {
        new QLearningSchedulingExample().run();
    }

    public QLearningSchedulingExample() {
        simulation = new CloudSimPlus();
        broker = new QLearningBroker(simulation, VMS);
        allCloudlets = new ArrayList<>();
        hostList = new ArrayList<>(HOSTS);
    }

    public void run() {
        Datacenter dc = createDatacenter(simulation);

        broker.setExplorationRate(1.0)
              .setEpsilonDecayRate(0.999)
              .setMinEpsilon(0.01)
              .setLearningRate(0.1)
              .setDiscountFactor(0.9)
              .setLaxityWeight(0.7)
              .setLifetimeWeight(0.3);

        this.vmList = createVms(VMS);
        broker.submitVmList(vmList);

        List<Cloudlet> initialCloudlets = createCloudlets(INITIAL_CLOUDLETS);
        allCloudlets.addAll(initialCloudlets);
        broker.submitCloudletList(initialCloudlets);

        simulation.addOnClockTickListener(this::createFixedNumberOfRandomCloudlets);
        simulation.start();

        List<Cloudlet> finishedList = broker.getCloudletFinishedList();
        new CloudletsTableBuilder(finishedList).build();
        System.out.println("Simulation finished!");

        // Metrics Calculation
        double makespan = finishedList.stream()
            .mapToDouble(Cloudlet::getFinishTime)
            .max().orElse(0.0);

        double totalWaitingTime = 0;
        int slaViolations = 0;
        int finishedCloudlets = finishedList.size();
        for (Cloudlet cloudlet : finishedList) {
            if (cloudlet.getStartTime() > 0) {
                 double waitingTime = cloudlet.getStartTime() - cloudlet.getSubmissionDelay();
                 totalWaitingTime += waitingTime;
            }

            if (cloudlet.getVm() != null && cloudlet.getVm().getMips() > 0 && cloudlet.getPesNumber() > 0) {
                double expectedDuration = cloudlet.getLength() / (cloudlet.getPesNumber() * cloudlet.getVm().getMips());
                double actualDuration = cloudlet.getFinishTime() - cloudlet.getStartTime();
                if (actualDuration > expectedDuration * 1.5) {
                    slaViolations++;
                }
            }
        }

        // Printing simulation results
        System.out.println("\n--- SIMULATION METRICS ---");
        System.out.printf("Makespan: %.2f s%n", makespan);
        System.out.printf("Average Waiting Time: %.2f s%n", (finishedCloudlets > 0 ? totalWaitingTime / finishedCloudlets : 0.0));
        System.out.printf("SLA Violations: %d%n", slaViolations);
        System.out.printf("Finished Cloudlets: %d%n", finishedCloudlets);
        System.out.printf("Total number of Cloudlets created: %d (Initial: %d, Dynamic: %d)%n",
                          allCloudlets.size(), INITIAL_CLOUDLETS, dynamicCloudletsCreatedCount);
        System.out.printf("Final Epsilon: %.4f%n", broker.getExplorationRate());
        System.out.println("DATACENTER getPowerModel:"+ dc.getPowerModel().getPower());
        printHostsCpuUtilizationAndPowerConsumption();
        printVmsCpuUtilizationAndPowerConsumption();
    }

    private Datacenter createDatacenter(CloudSimPlus sim) {
        for (int i = 0; i < HOSTS; i++) {
            this.hostList.add(createHost());
        }
        return new DatacenterSimple(sim, this.hostList);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        for (int i = 0; i < HOST_PES; i++) {
            peList.add(new PeSimple(HOST_MIPS));
        }

        var host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
        host.setVmScheduler(new VmSchedulerTimeShared());

        var powerModel = new PowerModelHostSimple(MAX_POWER, STATIC_POWER);
        powerModel
                  .setStartupPower(HOST_START_UP_POWER)
                  .setShutDownPower(HOST_SHUT_DOWN_POWER);
        host.setPowerModel(powerModel);
        host.enableUtilizationStats();

        return host;
    }

    private List<Vm> createVms(int count) {
        List<Vm> vms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
        	var vm= new VmSimple(VM_MIPS, VM_PES)
                    .setRam(2048).setBw(100000).setSize(4000)
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
                    vm.enableUtilizationStats();
            vms.add(vm);
        }
        return vms;
    }

    /**
     * MODIFIED: Creates Cloudlets with a random length selected from the CLOUDLET_LENGTHS list.
     */
    private List<Cloudlet> createCloudlets(int count) {
        final var utilizationModel = new UtilizationModelDynamic(0.2);
        List<Cloudlet> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Select a random length from the predefined list
            long cloudletLength = CLOUDLET_LENGTHS.get(random.nextInt(CLOUDLET_LENGTHS.size()));

            PrioritizedCloudlet cloudlet = new PrioritizedCloudlet(cloudletLength, CLOUDLET_PES, utilizationModel);
            cloudlet.setFileSize(300).setOutputSize(300)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelRam(utilizationModel).setUtilizationModelBw(utilizationModel);

            double expectedDuration = cloudlet.getLength() / (double) (CLOUDLET_PES * VM_MIPS);
            cloudlet.setDeadline(simulation.clock() + (expectedDuration * CLOUDLET_DEADLINE_FACTOR));
            list.add(cloudlet);
        }
        return list;
    }

    private void createFixedNumberOfRandomCloudlets(final EventInfo evt) {
        if (dynamicCloudletsCreatedCount < DYNAMIC_CLOUDLETS_TO_CREATE) {
            System.out.printf("%n# Creating dynamic Cloudlet at time %.2f%n", evt.getTime());
            Cloudlet cloudlet = createCloudlets(1).get(0);
            allCloudlets.add(cloudlet);
            broker.submitCloudlet(cloudlet);
            dynamicCloudletsCreatedCount++;
        }
    }

    private void printHostsCpuUtilizationAndPowerConsumption() {
        System.out.println("\n--- HOST ENERGY CONSUMPTION ---");
        for (final Host host : hostList) {
            printHostCpuUtilizationAndPowerConsumption(host);
        }
    }

    private void printHostCpuUtilizationAndPowerConsumption(final Host host) {
        final HostResourceStats cpuStats = host.getCpuUtilizationStats();
        final double utilizationPercentMean = cpuStats.getMean();
        final double watts = host.getPowerModel().getPower(utilizationPercentMean);
        System.out.printf(
            "Host %2d CPU Usage mean: %6.1f%% | Power Consumption mean: %8.2f W%n",
            host.getId(), utilizationPercentMean * 100, watts);
    }

    private void printVmsCpuUtilizationAndPowerConsumption() {
        System.out.println("\n--- VM ENERGY CONSUMPTION ---");
        vmList.sort(comparingLong(vm -> {
            // Handle cases where VM might not have a host
            return vm.getHost() != null ? vm.getHost().getId() : -1;
        }));
        for (Vm vm : vmList) {
            if(vm.getHost() == null || vm.getHost().isFailed()){
                System.out.printf("Vm %2d was not allocated to any Host or Host failed.%n", vm.getId());
                continue;
            }
            final var powerModel = vm.getHost().getPowerModel();
            // Avoid division by zero if no VMs were created on the host
            final int vmCountOnHost = vm.getHost().getVmCreatedList().size();
            if (vmCountOnHost == 0) continue;

            final double hostStaticPower = powerModel instanceof PowerModelHostSimple pms ? pms.getStaticPower() : 0;
            final double hostStaticPowerByVm = hostStaticPower / vmCountOnHost;

            final double vmRelativeCpuUtilization = vm.getCpuUtilizationStats().getMean() / vmCountOnHost;
            final double vmPower = powerModel.getPower(vmRelativeCpuUtilization) - hostStaticPower + hostStaticPowerByVm;
            final VmResourceStats cpuStats = vm.getCpuUtilizationStats();
            System.out.printf(
                "Vm %2d (on Host %d) CPU Usage Mean: %6.1f%% | Power Consumption Mean: %8.2f W%n",
                vm.getId(), vm.getHost().getId(), cpuStats.getMean() * 100, vmPower);
        }
    }
}

/**
 * A custom Cloudlet that includes a deadline.
 */
class PrioritizedCloudlet extends CloudletSimple {
    private double deadline;

    public PrioritizedCloudlet(long length, int pesNumber, UtilizationModelDynamic utilizationModel) {
        super(length, pesNumber, utilizationModel);
        this.deadline = -1; // -1 indicates no deadline
    }

    public double getDeadline() {
        return deadline;
    }

    public void setDeadline(double deadline) {
        this.deadline = deadline;
    }
}

/**
 * The Q-Learning broker for task scheduling.
 */
class QLearningBroker extends DatacenterBrokerSimple {
    private final QTable qTable;
    private double epsilon = 1.0;
    private double epsilonDecay = 0.999;
    private double minEpsilon = 0.01;
    private double alpha = 0.1;
    private double gamma = 0.9;
    private double delta1 = 0.7; // Weight for task laxity
    private double delta2 = 0.3; // Weight for task lifetime

    public QLearningBroker(CloudSimPlus sim, int numberOfVms) {
        super(sim);
        if (numberOfVms <= 0) {
            throw new IllegalArgumentException("Number of VMs must be positive.");
        }
        this.qTable = new QTable(numberOfVms);
    }

    //<editor-fold desc="Fluent Setters">
    public QLearningBroker setExplorationRate(double eps) { this.epsilon = eps; return this; }
    public QLearningBroker setEpsilonDecayRate(double decay) { this.epsilonDecay = decay; return this; }
    public QLearningBroker setMinEpsilon(double min) { this.minEpsilon = min; return this; }
    public QLearningBroker setLearningRate(double lr) { this.alpha = lr; return this; }
    public QLearningBroker setDiscountFactor(double df) { this.gamma = df; return this; }
    public QLearningBroker setLaxityWeight(double d1) { this.delta1 = d1; return this; }
    public QLearningBroker setLifetimeWeight(double d2) { this.delta2 = d2; return this; }
    public double getExplorationRate() { return this.epsilon; }
    //</editor-fold>

    @Override
    public List<Cloudlet> getCloudletWaitingList() {
        final List<Cloudlet> waitingList = super.getCloudletWaitingList();
        if (waitingList.isEmpty()) {
            return waitingList;
        }
        waitingList.sort(Comparator.comparingDouble(this::calculatePriority));
        return waitingList;
    }

    private double calculatePriority(Cloudlet cloudlet) {
        PrioritizedCloudlet pCloudlet = (PrioritizedCloudlet) cloudlet;
        double currentTime = getSimulation().clock();
        double estimatedExecTime = pCloudlet.getLength() / (double) (pCloudlet.getPesNumber() * 1000);
        double laxity = (pCloudlet.getDeadline() - currentTime) - estimatedExecTime;
        double lifetime = currentTime - pCloudlet.getSubmissionDelay();
        return delta1 * laxity - delta2 * lifetime;
    }

    @Override
    protected Vm defaultVmMapper(final Cloudlet cloudlet) {
        final List<Vm> vmList = getVmExecList();
        if (vmList.isEmpty()) {
            LOGGER.warn("{}: {}: No VMs available for {}. Postponing execution.", getSimulation().clock(), getName(), cloudlet);
            return Vm.NULL;
        }

        final int state = observeState();
        int action;

        if (Math.random() < epsilon) {
            action = new Random().nextInt(vmList.size()); // Explore
        } else {
            action = qTable.getBestAction(state); // Exploit
        }

        Vm selectedVm = vmList.get(action);
        final double reward = calculateReward(cloudlet, selectedVm);
        final int newState = observeStateAfterAction(action);
        final double oldQ = qTable.getQValue(state, action);
        final double maxQ = qTable.getMaxQValue(newState);
        final double updatedQ = oldQ + alpha * (reward + gamma * maxQ - oldQ);
        qTable.update(state, action, updatedQ);

        if (epsilon > minEpsilon) {
            epsilon *= epsilonDecay;
        }
        return selectedVm;
    }

    private int observeState() {
        List<Integer> queueSizes = getVmExecList().stream()
            .map(vm -> vm.getCloudletScheduler().getCloudletWaitingList().size())
            .collect(Collectors.toList());
        return queueSizes.toString().hashCode();
    }

    private int observeStateAfterAction(int vmIndex) {
        List<Integer> queueSizes = new ArrayList<>();
        for(int i=0; i<getVmExecList().size(); i++) {
            int queueSize = getVmExecList().get(i).getCloudletScheduler().getCloudletWaitingList().size();
            if (i == vmIndex) {
                queueSize++;
            }
            queueSizes.add(queueSize);
        }
        return queueSizes.toString().hashCode();
    }

    private double calculateReward(Cloudlet cloudlet, Vm selectedVm) {
        PrioritizedCloudlet pCloudlet = (PrioritizedCloudlet) cloudlet;
        double minWaitingTime = Double.MAX_VALUE;
        for (Vm vm : getVmExecList()) {
            minWaitingTime = Math.min(minWaitingTime, getExpectedWaitingTime(vm));
        }

        double selectedVmWaitingTime = getExpectedWaitingTime(selectedVm);
        double estimatedExecTime = pCloudlet.getLength() / selectedVm.getMips();
        boolean meetsDeadline = (getSimulation().clock() + selectedVmWaitingTime + estimatedExecTime) < pCloudlet.getDeadline();
        boolean isBestChoice = selectedVmWaitingTime <= minWaitingTime;

        if (meetsDeadline && isBestChoice) return 1.0;
        if (meetsDeadline) return 0.0;
        return -1.0;
    }

    private double getExpectedWaitingTime(Vm vm) {
        double waitingTime = 0;
        if (vm.isWorking() && !vm.getCloudletScheduler().getCloudletExecList().isEmpty()) {
            waitingTime += vm.getCloudletScheduler().getCloudletExecList().get(0).getExpectedFinishTime();
        }
        
        for (CloudletExecution ce : vm.getCloudletScheduler().getCloudletWaitingList()) {
            waitingTime += ce.getCloudlet().getLength() / vm.getMips();
        }
        return waitingTime;
    }
}

/**
 * The Q-Table implementation.
 */
class QTable {
    private final Map<Integer, double[]> table = new HashMap<>();
    private final int numActions;

    public QTable(int numActions) {
        this.numActions = numActions;
    }

    private double[] getQValues(int state) {
        return table.computeIfAbsent(state, k -> new double[numActions]);
    }

    int getBestAction(int state) {
        double[] qValues = getQValues(state);
        int bestAction = 0;
        for (int i = 1; i < qValues.length; i++) {
            if (qValues[i] > qValues[bestAction]) {
                bestAction = i;
            }
        }
        return bestAction;
    }

    double getMaxQValue(int state) {
        return Arrays.stream(getQValues(state)).max().orElse(0.0);
    }

    double getQValue(int state, int action) {
        if (action < 0 || action >= numActions) return 0.0;
        return getQValues(state)[action];
    }

    void update(int state, int action, double value) {
        if (action < 0 || action >= numActions) return;
        getQValues(state)[action] = value;
    }
}

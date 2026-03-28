package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;
import java.io.*;

public class QLearningParamSearchRunner {
    private static final int EPISODES = 5000;
    private static final String CLOUDLET_CSV = "cloudlets.csv";
    private static final int INITIAL_CLOUDLETS = 10;
    private static final int DYNAMIC_CLOUDLETS_TO_CREATE = 100;
    private static final int CLOUDLET_PES = 2;
    private static final int VMS = 16;
    private static final int HOSTS = 4;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 2000;
    private static final int HOST_RAM = 16_384;
    private static final long HOST_BW = 100_000_000L;
    private static final long HOST_STORAGE = 1_000_000L;
    private static final double STATIC_POWER = 35;
    private static final int MAX_POWER = 50;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;
    private static final double SIMULATION_DURATION = 2000.0;
    private static final double CLOUDLET_DEADLINE_FACTOR = 3.0;

    // Parameter ranges
    private static final double[] LEARNING_RATES = {0.03, 0.05, 0.07, 0.09, 0.11, 0.13};
    private static final double[] DISCOUNT_FACTORS = {0.7, 0.75, 0.8, 0.85, 0.9};
    private static final double[] EPS_DECAYS = {0.995, 0.997, 0.999};
    private static final double[] MIN_EPSILONS = {0.01, 0.05, 0.1};
    private static final double[] LAXITY_WEIGHTS = {0.4, 0.5, 0.6, 0.7, 0.8};
    private static final double[] LIFETIME_WEIGHTS = {0.2, 0.3, 0.4, 0.5, 0.6};


    private static List<Cloudlet> allCloudlets;

    public static void main(String[] args) throws IOException {
        allCloudlets = loadCloudletsFromCsv(CLOUDLET_CSV, CLOUDLET_PES, new UtilizationModelDynamic(0.2));

        PrintWriter log = new PrintWriter(new FileWriter("qlearning_param_search_log.csv"));
        log.println("Episode,ExplorationRate,EpsilonDecay,MinEpsilon,LearningRate,DiscountFactor,LaxityWeight,LifetimeWeight,Makespan,TimeResponse,AvgWaiting,TotalEnergy,IsBest");

        // Track the best Q-learning parameters and metrics
        double bestMakespan = Double.MAX_VALUE, bestResp = Double.MAX_VALUE, bestWait = Double.MAX_VALUE, bestEnergy = Double.MAX_VALUE;
        String bestParams = "";

        Random rand = new Random();

        for (int ep = 1; ep <= EPISODES; ep++) {
            // Sample Q-learning parameters
            double explorationRate = 1.0;
            double epsilonDecay = EPS_DECAYS[rand.nextInt(EPS_DECAYS.length)];
            double minEpsilon = MIN_EPSILONS[rand.nextInt(MIN_EPSILONS.length)];
            double learningRate = LEARNING_RATES[rand.nextInt(LEARNING_RATES.length)];
            double discountFactor = DISCOUNT_FACTORS[rand.nextInt(DISCOUNT_FACTORS.length)];
            double laxityWeight = LAXITY_WEIGHTS[rand.nextInt(LAXITY_WEIGHTS.length)];
            double lifetimeWeight = LIFETIME_WEIGHTS[rand.nextInt(LIFETIME_WEIGHTS.length)];

            // Run Q-learning
            SimulationResult qRes = runSim("Q-Learning", sim -> {
                var b = new QLearningBroker(sim, VMS);
                b.setExplorationRate(explorationRate)
                 .setEpsilonDecayRate(epsilonDecay)
                 .setMinEpsilon(minEpsilon)
                 .setLearningRate(learningRate)
                 .setDiscountFactor(discountFactor)
                 .setLaxityWeight(laxityWeight)
                 .setLifetimeWeight(lifetimeWeight);
                return b;
            });

            // Run baselines
            SimulationResult fcfsRes = runSim("FCFS", sim -> new DatacenterBrokerSimple(sim));
            SimulationResult sjfRes = runSim("SJF", sim -> new SjfBroker(sim));
            SimulationResult rrRes = runSim("Round Robin", sim -> new RoundRobinBroker(sim));

            // Compare metrics
            boolean isBest = qRes.makespan < Math.min(Math.min(fcfsRes.makespan, sjfRes.makespan), rrRes.makespan) &&
                             qRes.timeResponse < Math.min(Math.min(fcfsRes.timeResponse, sjfRes.timeResponse), rrRes.timeResponse) &&
                             qRes.avgWaiting < Math.min(Math.min(fcfsRes.avgWaiting, sjfRes.avgWaiting), rrRes.avgWaiting) &&
                             qRes.totalEnergy < Math.min(Math.min(fcfsRes.totalEnergy, sjfRes.totalEnergy), rrRes.totalEnergy);

            if (isBest &&
                (qRes.makespan < bestMakespan ||
                 qRes.timeResponse < bestResp ||
                 qRes.avgWaiting < bestWait ||
                 qRes.totalEnergy < bestEnergy)) {
                bestMakespan = qRes.makespan;
                bestResp = qRes.timeResponse;
                bestWait = qRes.avgWaiting;
                bestEnergy = qRes.totalEnergy;
                bestParams = String.format("Episode %d: ExplorationRate=%.2f, EpsilonDecay=%.3f, MinEpsilon=%.2f, LearningRate=%.2f, DiscountFactor=%.2f, LaxityWeight=%.2f, LifetimeWeight=%.2f",
                        ep, explorationRate, epsilonDecay, minEpsilon, learningRate, discountFactor, laxityWeight, lifetimeWeight);
                // Optionally, save best metrics to a file
                try (PrintWriter best = new PrintWriter(new FileWriter("qlearning_best_params.txt"))) {
                    best.println(bestParams);
                    best.printf("Makespan=%.2f, TimeResponse=%.2f, AvgWaiting=%.2f, TotalEnergy=%.2f%n",
                            bestMakespan, bestResp, bestWait, bestEnergy);
                }
            }

            log.printf("%d,%.2f,%.3f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%b%n",
                    ep, explorationRate, epsilonDecay, minEpsilon, learningRate, discountFactor, laxityWeight, lifetimeWeight,
                    qRes.makespan, qRes.timeResponse, qRes.avgWaiting, qRes.totalEnergy, isBest);
        }
        log.close();
    }

    private static SimulationResult runSim(String name, BrokerFactory factory) {
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBroker broker = factory.create(simulation);
        List<Host> hostList = new ArrayList<>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            var peList = new ArrayList<Pe>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(HOST_MIPS));
            }
            var host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
            host.setVmScheduler(new org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(MAX_POWER, STATIC_POWER));
            host.enableUtilizationStats();
            hostList.add(host);
        }
        Datacenter dc = new DatacenterSimple(simulation, hostList);

        // VMs
        var vms = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            var vm = new VmSimple(VM_MIPS, VM_PES)
                .setRam(2048).setBw(100_000).setSize(4_000)
                .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vm.enableUtilizationStats();
            vms.add(vm);
        }
        broker.submitVmList(vms);

        // Initial and dynamic cloudlets (deep copy for each run)
        List<Cloudlet> runCloudlets = cloneCloudletList(allCloudlets);

        List<Cloudlet> initial = new ArrayList<>();
        for (int i = 0; i < INITIAL_CLOUDLETS && i < runCloudlets.size(); i++) {
            initial.add(runCloudlets.get(i));
        }
        broker.submitCloudletList(initial);

        final int[] dynamicCreated = {0};
        simulation.addOnClockTickListener(evt -> {
            int next = INITIAL_CLOUDLETS + dynamicCreated[0];
            if (next < INITIAL_CLOUDLETS + DYNAMIC_CLOUDLETS_TO_CREATE && next < runCloudlets.size()) {
                broker.submitCloudlet(runCloudlets.get(next));
                dynamicCreated[0]++;
            }
        });

        simulation.terminateAt(SIMULATION_DURATION);
        simulation.start();

        // Collect metrics
        var finished = broker.getCloudletFinishedList();
        SimulationResult res = new SimulationResult(name);

        double makespan = finished.stream()
            .mapToDouble(Cloudlet::getFinishTime)
            .max().orElse(0.0);
        res.setMakespan(makespan);

        double resp = finished.stream()
            .mapToDouble(Cloudlet::getFinishTime)
            .average().orElse(0.0);
        res.setTimeResponse(resp);

        double waiting = finished.stream()
            .mapToDouble(c -> c.getStartTime() - c.getSubmissionDelay())
            .average().orElse(0.0);
        res.setAvgWaitingTime(waiting);

        double totalEnergy = hostList.stream()
                .mapToDouble(host -> host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean()))
                .sum();
        res.setTotalEnergy(totalEnergy);

        return res;
    }

    /**
     * Loads cloudlets from a CSV file (semicolon separated, with header).
     * Each line: cloudletId;length;FileSize;OutputSize
     * Returns a list of Cloudlet objects.
     */
    private static List<Cloudlet> loadCloudletsFromCsv(
            String filename, int pesNumber, UtilizationModelDynamic utilizationModel) throws IOException {

        List<Cloudlet> cloudlets = new ArrayList<>();
        var cpuUtil = new UtilizationModelFull();
        var dynUtil = new UtilizationModelDynamic(0.2);

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // skip header
                String[] parts = line.split(";");
                if (parts.length < 4) continue;
                int id = Integer.parseInt(parts[0]);
                long length = Long.parseLong(parts[1]);
                long fileSize = Long.parseLong(parts[2]);
                long outputSize = Long.parseLong(parts[3]);

                var c = new PrioritizedCloudlet(length, pesNumber, dynUtil);
                c.setUtilizationModelCpu(cpuUtil)
                 .setUtilizationModelRam(dynUtil)
                 .setUtilizationModelBw(dynUtil)
                 .setFileSize(fileSize).setOutputSize(outputSize);

                double exec = length / (double)(pesNumber * VM_MIPS);
                c.setDeadline(exec * CLOUDLET_DEADLINE_FACTOR);
                cloudlets.add(c);
            }
        }
        return cloudlets;
    }

    // Utility to deep-clone the cloudlet list for each run
    private static List<Cloudlet> cloneCloudletList(List<Cloudlet> original) {
        List<Cloudlet> copy = new ArrayList<>();
        for (Cloudlet c : original) {
            copy.add(cloneCloudlet(c));
        }
        return copy;
    }

    // Deep clone a Cloudlet (to avoid state sharing between runs)
    private static Cloudlet cloneCloudlet(Cloudlet original) {
        var cpuUtil = new UtilizationModelFull();
        var dynUtil = new UtilizationModelDynamic(0.2);

        var c = new PrioritizedCloudlet(original.getLength(), (int) (original.getPesNumber()), dynUtil);

        c.setUtilizationModelCpu(cpuUtil)
         .setUtilizationModelRam(dynUtil)
         .setUtilizationModelBw(dynUtil)
         .setFileSize(original.getFileSize()).setOutputSize(original.getOutputSize());

        double exec = original.getLength() / (double)(CLOUDLET_PES * VM_MIPS);
        c.setDeadline(exec * CLOUDLET_DEADLINE_FACTOR);
        return c;
    }

    private interface BrokerFactory {
        DatacenterBroker create(CloudSimPlus sim);
    }

    private static class SimulationResult {
        final String name;
        double makespan, timeResponse, avgWaiting, totalEnergy;
        SimulationResult(String n) { name = n; }
        void setMakespan(double m)   { makespan = m; }
        void setTimeResponse(double t){ timeResponse = t; }
        void setAvgWaitingTime(double w){ avgWaiting = w; }
        void setTotalEnergy(double e){ totalEnergy = e; }
    }
}

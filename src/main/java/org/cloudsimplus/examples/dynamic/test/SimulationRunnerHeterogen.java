package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
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
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.*;
import java.io.*;

public class SimulationRunnerHeterogen {
    // Configuration constants
    private static final int HOSTS = 4;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 2000;
    private static final int HOST_RAM = 16_384;
    private static final long HOST_BW = 100_000_000L;
    private static final long HOST_STORAGE = 1_000_000L;
    private static final double STATIC_POWER = 35;
    private static final int MAX_POWER = 50;

    private static final int VMS = 16;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;

    private static final int INITIAL_CLOUDLETS = 10;
    private static final int DYNAMIC_CLOUDLETS_TO_CREATE = 100;
    private static final int CLOUDLET_PES = 2;
    private static final double SIMULATION_DURATION = 2000.0;
    private static final double CLOUDLET_DEADLINE_FACTOR = 3.0;
    
    private static final String CLOUDLET_CSV = "cloudlets.csv";

    private static CloudSimPlus simulation;
    private DatacenterBroker broker;
    private List<Host> hostList;
    private static List<Cloudlet> allCloudlets; // Loaded from CSV, static so all runs share the same list
    private int dynamicCreated = 0;

    public static void main(String[] args) {
        var results = new ArrayList<SimulationResult>();

        results.add(new SimulationRunnerHeterogen()
            .runSimulation("Q-Learning",
                sim -> {
                    var b = new QLearningBroker(sim, VMS);
                    b.setExplorationRate(1.0)
                    .setEpsilonDecayRate(0.999)
                    .setMinEpsilon(0.1)
                    .setLearningRate(0.1)
                    .setDiscountFactor(0.75)
                    .setLaxityWeight(0.4)
                    .setLifetimeWeight(0.5);
                    return b;
                }));
        results.add(new SimulationRunnerHeterogen()
        	    .runSimulation("DCOA", sim -> new DCOABroker(sim)));
        results.add(new SimulationRunnerHeterogen()
        	    .runSimulation("PSO-SA Hybrid", sim -> new PSOSABroker(sim)));
        
        //results.add(new SimulationRunnerHeterogen()
        	//    .runSimulation("Hybrid PSO-SA Cloud", sim -> new HybridPSOSACloudBroker(sim)));
        
        results.add(new SimulationRunnerHeterogen()
            .runSimulation("FCFS",
                sim -> new DatacenterBrokerSimple(sim)));

        results.add(new SimulationRunnerHeterogen()
            .runSimulation("Round Robin",
                sim -> new RoundRobinBroker(sim)));

        results.add(new SimulationRunnerHeterogen()
            .runSimulation("SJF",
                sim -> new SjfBroker(sim)));

        results.forEach(System.out::println);
        exportCsv(results, "simulation_results.csv");
    }

    public SimulationResult runSimulation(
        String name, BrokerFactory factory) {
        this.simulation = new CloudSimPlus();
        this.broker = factory.create(simulation);
        Datacenter dc = createDatacenter();
        var vms = createVms();
        broker.submitVmList(vms);

        // Load cloudlets from CSV ONCE (for all runs)
        if (allCloudlets == null) {
            try {
                allCloudlets = loadCloudletsFromCsv(CLOUDLET_CSV, CLOUDLET_PES, new UtilizationModelDynamic(0.2));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load cloudlets from CSV", e);
            }
        }

        // Submit initial batch
        List<Cloudlet> initial = new ArrayList<>();
        for (int i = 0; i < INITIAL_CLOUDLETS && i < allCloudlets.size(); i++) {
            // Clone cloudlet for each run to avoid state sharing
            initial.add(cloneCloudlet(allCloudlets.get(i)));
        }
        broker.submitCloudletList(initial);

        // For dynamic arrivals, keep an index
        dynamicCreated = 0;
        simulation.addOnClockTickListener(evt -> {
            int next = INITIAL_CLOUDLETS + dynamicCreated;
            if (next < INITIAL_CLOUDLETS + DYNAMIC_CLOUDLETS_TO_CREATE && next < allCloudlets.size()) {
                broker.submitCloudlet(cloneCloudlet(allCloudlets.get(next)));
                dynamicCreated++;
            }
        });

        simulation.terminateAt(SIMULATION_DURATION);
        simulation.start();
        return collect(name, dc);
    }

    private Datacenter createDatacenter() {
        hostList = new ArrayList<>(HOSTS);
        // Example: 4 hosts with different resources
        int[] hostRam =    {16384, 8192, 32768, 24576};        // MB
        int[] hostPes =    {8, 4, 16, 12};
        int[] hostMips =   {2000, 1000, 2500, 1500};
        long[] hostBw =    {100_000_000L, 50_000_000L, 200_000_000L, 120_000_000L};
        long[] hostStorage = {1_000_000L, 500_000L, 2_000_000L, 1_500_000L};
        double[] staticPower = {35, 30, 40, 32};
        int[] maxPower = {50, 45, 60, 55};

        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>(hostPes[i]);
            for (int j = 0; j < hostPes[i]; j++) {
                peList.add(new PeSimple(hostMips[i]));
            }
            HostSimple host = new HostSimple(hostRam[i], hostBw[i], hostStorage[i], peList);
            host.setVmScheduler(new org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(maxPower[i], staticPower[i]));
            host.enableUtilizationStats();
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }


    private List<Vm> createVms() {
        var list = new ArrayList<Vm>(VMS);
        // Example: 16 VMs with different resources
        int[] vmRam =    {2048, 4096, 1024, 3072, 2048, 6144, 2048, 8192, 4096, 2048, 1024, 4096, 2048, 3072, 2048, 2048};
        int[] vmPes =    {2, 2, 1, 4, 2, 4, 2, 6, 4, 2, 1, 4, 2, 4, 2, 2};
        int[] vmMips =   {1000, 1500, 500, 2000, 1200, 1800, 1000, 2500, 1600, 1000, 800, 2000, 1200, 1800, 1000, 1000};
        long[] vmBw =    {100_000, 200_000, 50_000, 150_000, 100_000, 250_000, 100_000, 300_000, 200_000, 100_000, 50_000, 150_000, 100_000, 250_000, 100_000, 50_000};
        long[] vmSize =  {4000, 8000, 2000, 6000, 4000, 10000, 4000, 12000, 8000, 4000, 2000, 6000, 4000, 10000, 4000, 2000};

       
        for (int i = 0; i < VMS; i++) {
            VmSimple vm = new VmSimple(vmMips[i], vmPes[i]);
            vm.setRam(vmRam[i])
                .setBw(vmBw[i])
                .setSize(vmSize[i])
                .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vm.enableUtilizationStats();
            list.add(vm);
        }
        return list;
    }

    /**
     * Loads cloudlets from a CSV file (semicolon separated, with header).
     * Each line: cloudletId;length;FileSize;OutputSize
     * Returns a list of Cloudlet objects.
     */
    private static List<Cloudlet> loadCloudletsFromCsv(
            String filename, int pesNumber, UtilizationModelDynamic utilizationModel) throws IOException {
    	int nbrDynamicCloudlet=0;
    	var cpuUtil = new UtilizationModelFull();
        var dynUtil = new UtilizationModelDynamic(0.2);
                
        List<Cloudlet> cloudlets = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean first = true;
            while (((line = br.readLine()) != null)&& nbrDynamicCloudlet<DYNAMIC_CLOUDLETS_TO_CREATE) {
                if (first) { first = false; continue; } // skip header
                String[] parts = line.split(";");
                if (parts.length < 4) continue;
                nbrDynamicCloudlet++;
                int id = Integer.parseInt(parts[0]);
                long length = Long.parseLong(parts[1]);
                long fileSize = Long.parseLong(parts[2]);
                long outputSize = Long.parseLong(parts[3]);
        
                var c = new PrioritizedCloudlet(length, CLOUDLET_PES, dynUtil);
                c.setUtilizationModelCpu(cpuUtil)
                 .setUtilizationModelRam(dynUtil)
                 .setUtilizationModelBw(dynUtil)
                 .setFileSize(fileSize).setOutputSize(outputSize);

                double exec = length / (double)(CLOUDLET_PES * VM_MIPS);
                c.setDeadline(simulation.clock() + exec * CLOUDLET_DEADLINE_FACTOR);
                cloudlets.add(c);
            }
        }
        return cloudlets;
    }

    /**
     * Deep clone a Cloudlet (to avoid state sharing between runs).
     */
    private static Cloudlet cloneCloudlet(Cloudlet original) {
    	var cpuUtil = new UtilizationModelFull();
        var dynUtil = new UtilizationModelDynamic(0.2);
    
    	var c = new PrioritizedCloudlet(original.getLength(), (int) (original.getPesNumber()), dynUtil);
     
    	c.setUtilizationModelCpu(cpuUtil)
         .setUtilizationModelRam(dynUtil)
         .setUtilizationModelBw(dynUtil)
         .setFileSize(original.getFileSize()).setOutputSize(original.getOutputSize());

        double exec = original.getLength() / (double)(CLOUDLET_PES * VM_MIPS);
        c.setDeadline(simulation.clock() + exec * CLOUDLET_DEADLINE_FACTOR);
    	return c;
    }

    private SimulationResult collect(String name, Datacenter dc) {
        var finished = broker.getCloudletFinishedList();
        var res = new SimulationResult(name);

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

    private static void exportCsv(List<SimulationResult> list, String file) {
        try (var pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Algorithm,Makespan,TimeResponse,AvgWaitingTime,TotalEnergy");
            for (var r : list) {
                pw.printf("%s,%.2f,%.2f,%.2f,%.2f%n",
                   r.name, r.makespan, r.timeResponse, r.avgWaiting, r.totalEnergy);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        @Override public String toString() {
            return String.format(
              "%s → makespan=%.2f, resp=%.2f, wait=%.2f, energy=%.2f",
               name, makespan, timeResponse, avgWaiting, totalEnergy);
        }
    }
}

package org.cloudsimplus.examples.dynamic.teststatic;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
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

public class StaticSimulationRunnerHeterogen {

    private static final int HOSTS = 4;
    private static final int VMS = 16;
    private static final int CLOUDLET_PES = 1;
    private static final String CLOUDLET_CSV = "cloudlets.csv";

    // Heterogeneous host configuration
    private static final int[] HOST_RAM = {16384, 8192, 32768, 24576}; // MB
    private static final int[] HOST_PES = {8, 4, 16, 12};
    private static final int[] HOST_MIPS = {2000, 1000, 2500, 1500};
    private static final long[] HOST_BW = {100_000_000L, 50_000_000L, 200_000_000L, 120_000_000L};
    private static final long[] HOST_STORAGE = {1_000_000L, 500_000L, 2_000_000L, 1_500_000L};
    private static final double[] STATIC_POWER = {35, 30, 40, 32};
    private static final int[] MAX_POWER = {50, 45, 60, 55};
    private static final int NBR_CL = 1000; // MB
    
    // Heterogeneous VM configuration
    private static final int[] VM_RAM = {2048, 4096, 1024, 3072, 2048, 6144, 2048, 8192, 4096, 4096, 1024, 4096, 2048, 3072, 2048, 2048};
    private static final int[] VM_PES = {1, 2, 4, 4, 1, 4, 2, 4, 4, 1, 2, 4, 2, 4, 2, 4};
    private static final int[] VM_MIPS = {1000, 1500, 1000, 2000, 1200, 1800, 1000, 2500, 1600, 1000, 1400, 2000, 1200, 1800, 1000, 1000};
    private static final long[] VM_BW = {100_000, 200_000, 50_000, 150_000, 100_000, 250_000, 100_000, 300_000, 200_000, 100_000, 50_000, 150_000, 100_000, 250_000, 100_000, 50_000};
    private static final long[] VM_SIZE = {4000, 8000, 4000, 6000, 4000, 10000, 4000, 12000, 8000, 8000, 4000, 6000, 4000, 10000, 4000, 2000};

    public static void main(String[] args) {
        List<SimulationResult> results = new ArrayList<>();

        results.add(runSimulation("FCFS", StaticSimulationRunnerHeterogen::fcfsMapping));
        results.add(runSimulation("RoundRobin", StaticSimulationRunnerHeterogen::roundRobinMapping));
        results.add(runSimulation("SJF", StaticSimulationRunnerHeterogen::sjfMapping));
        results.add(runSimulation("DCOA", StaticSimulationRunnerHeterogen::dcoaMapping));
        results.add(runSimulation("HybridPSOSA", StaticSimulationRunnerHeterogen::hybridPsosaMapping));
        results.add(runSimulation("PSO-SA", StaticSimulationRunnerHeterogen::psosaMapping));
        results.add(runSimulation("Qlearning", StaticSimulationRunnerHeterogen::qlearningMapping));

        results.forEach(System.out::println);
    }

    private static SimulationResult runSimulation(String name, StaticMapper mapper) {
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        Datacenter datacenter = createDatacenter(simulation);
        List<Vm> vms = createVms();
        List<Cloudlet> cloudlets = new ArrayList<>();
        try {
            cloudlets = loadCloudletsFromCsv(CLOUDLET_CSV, CLOUDLET_PES, new UtilizationModelDynamic(0.2),NBR_CL);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load cloudlets from CSV", e);
        }

        mapper.map(cloudlets, vms);
        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        //simulation.terminateAt(10000);
        datacenter.setSchedulingInterval(0.5);     // Example: set to 0.5s
        broker.setVmDestructionDelay(20000);
        simulation.start();
        return collectResults(name, broker, datacenter);
    }

    private static Datacenter createDatacenter(CloudSimPlus sim) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < HOST_PES[i]; j++) {
                peList.add(new PeSimple(HOST_MIPS[i]));
            }
            HostSimple host = new HostSimple(HOST_RAM[i], HOST_BW[i], HOST_STORAGE[i], peList);
            host.setVmScheduler(new org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(MAX_POWER[i], STATIC_POWER[i]));
            host.enableUtilizationStats();
            hosts.add(host);
        }
        return new DatacenterSimple(sim, hosts);
    }

    private static List<Vm> createVms() {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(VM_MIPS[i], VM_PES[i])
                    .setRam(VM_RAM[i])
                    .setBw(VM_BW[i])
                    .setSize(VM_SIZE[i])
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vm.enableUtilizationStats();
            vms.add(vm);
        }
        return vms;
    }

    /**
     * Loads cloudlets from a CSV file (semicolon separated, with header).
     * Each line: cloudletId;length;FileSize;OutputSize
     * Returns a list of Cloudlet objects.
     */
    private static List<Cloudlet> loadCloudletsFromCsv(
            String filename, int pesNumber, UtilizationModelDynamic utilizationModel, int nbrCl) throws IOException {

        var cpuUtil = new UtilizationModelFull();
        List<Cloudlet> cloudlets = new ArrayList<>();
        int nbr=0;
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean first = true;
            while (((line = br.readLine()) != null) && (nbr<=nbrCl)) {
                if (first) {
                    first = false;
                    continue;
                }
                nbr++;
                String[] parts = line.split(";");
                long length = Long.parseLong(parts[1]);
                long fileSize = Long.parseLong(parts[2]);
                long outputSize = Long.parseLong(parts[3]);
                CloudletSimple cloudlet = new CloudletSimple(length, pesNumber);
                cloudlet.setFileSize(fileSize);
                cloudlet.setOutputSize(outputSize);
                cloudlet.setUtilizationModelCpu(cpuUtil);
                cloudlet.setUtilizationModelRam(utilizationModel);
                cloudlet.setUtilizationModelBw(utilizationModel);
                cloudlets.add(cloudlet);
            }
        }
        return cloudlets;
    }

    // --- Static mapping strategies ---

    private static void fcfsMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setVm(vms.get(0));
        }
    }

    private static void roundRobinMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVm(vms.get(i % vms.size()));
        }
    }

    private static void sjfMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        cloudlets.sort(Comparator.comparingLong(Cloudlet::getLength));
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVm(vms.get(i % vms.size()));
        }
    }

    private static void dcoaMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        DCOAMapper dcoa = new DCOAMapper(vms);
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setVm(vms.get(dcoa.findBestVmIndex(cloudlet)));
        }
    }

    private static void psosaMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        PSOSAMapper psosa = new PSOSAMapper(vms);
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setVm(vms.get(psosa.findBestVmIndex(cloudlet)));
        }
    }

    private static void hybridPsosaMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        HybridPSOSAMapper hybrid = new HybridPSOSAMapper(vms);
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setVm(vms.get(hybrid.findBestVmIndex(cloudlet)));
        }
    }

    private static void qlearningMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        QLearningMapper qlMapper = new QLearningMapper(vms);
        qlMapper.map(cloudlets);
    }

    private static SimulationResult collectResults(String name, DatacenterBrokerSimple broker, Datacenter datacenter) {
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        SimulationResult res = new SimulationResult(name);
        double makespan = finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);
        res.setMakespan(makespan);
        double totalWaiting = finished.stream().mapToDouble(c -> c.getStartTime() - c.getSubmissionDelay()).sum();
        res.setAvgWaitingTime(finished.isEmpty() ? 0.0 : totalWaiting / finished.size());
        double totalEnergy = datacenter.getHostList().stream()
                .mapToDouble(host -> host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean()))
                .sum();
        res.setTotalEnergyConsumption(totalEnergy);
        res.setFinishedCloudlets(finished.size());
        return res;
    }

    private interface StaticMapper {
        void map(List<Cloudlet> cloudlets, List<Vm> vms);
    }
}

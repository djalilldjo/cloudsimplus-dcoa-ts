package org.cloudsimplus.examples.dynamic.teststatic;
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

public class StaticSimulationRunner {

    private static final int HOSTS = 4;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 2000;
    private static final int HOST_RAM = 16384;
    private static final long HOST_BW = 100000000L;
    private static final long HOST_STORAGE = 1000000L;
    private static final double STATIC_POWER = 35;
    private static final int MAX_POWER = 50;
    private static final int VMS = 16;
    private static final int VM_PES = 4;
    private static final int VM_MIPS = 1000;
    private static final int CLOUDLETS = 100;
    private static final int CLOUDLET_PES = 2;
    private static final List<Long> CLOUDLET_LENGTHS =
            Arrays.asList(1000L, 1500L, 2000L, 2500L, 3000L, 3500L, 4000L, 4500L, 5000L);

    public static void main(String[] args) {
        List<SimulationResult> results = new ArrayList<>();
        results.add(runSimulation("FCFS", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::fcfsMapping));
        results.add(runSimulation("RoundRobin", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::roundRobinMapping));
        results.add(runSimulation("SJF", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::sjfMapping));
        results.add(runSimulation("DCOA", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::dcoaMapping));
        results.add(runSimulation("PSO-SA", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::psosaMapping));
        results.add(runSimulation("HybridPSOSA", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::hybridPsosaMapping));
        results.add(runSimulation("Qlearning", sim -> new DatacenterBrokerSimple(sim), StaticSimulationRunner::qlearningMapping));
        results.forEach(System.out::println);
    }

    private static SimulationResult runSimulation(String name, BrokerFactory factory, StaticMapper mapper) {
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBroker broker = factory.create(simulation);
        Datacenter dc = createDatacenter(simulation);
        List<Vm> vms = createVms();
        List<Cloudlet> cloudlets = createCloudlets();

        // Static mapping before simulation
        mapper.map(cloudlets, vms);

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);

        simulation.start();

        return collectResults(name, broker, dc);
    }

    private static Datacenter createDatacenter(CloudSimPlus sim) {
        List<Host> hosts = new ArrayList<>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(HOST_MIPS));
            }
            HostSimple host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
            host.setVmScheduler(new org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(MAX_POWER, STATIC_POWER));
            host.enableUtilizationStats();
            hosts.add(host);
        }
        return new DatacenterSimple(sim, hosts);
    }

    private static List<Vm> createVms() {
        List<Vm> vms = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(VM_MIPS, VM_PES)
                    .setRam(2048).setBw(100000).setSize(4000)
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vm.enableUtilizationStats();
            vms.add(vm);
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets() {
        Random random = new Random(42);
        List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        UtilizationModelDynamic dynUtil = new UtilizationModelDynamic(0.2);
        for (int i = 0; i < CLOUDLETS; i++) {
            long length = CLOUDLET_LENGTHS.get(random.nextInt(CLOUDLET_LENGTHS.size()));
            PrioritizedCloudlet cloudlet = new PrioritizedCloudlet(length, CLOUDLET_PES, dynUtil);
            cloudlet.setFileSize(300).setOutputSize(300)
                    .setUtilizationModelCpu(new UtilizationModelFull())
                    .setUtilizationModelRam(dynUtil).setUtilizationModelBw(dynUtil);
            double expectedDuration = length / (double) (CLOUDLET_PES * VM_MIPS);
            cloudlet.setDeadline(expectedDuration * 3.0);
            list.add(cloudlet);
        }
        return list;
    }

    // --- Static mapping strategies ---

    private static void fcfsMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVm(vms.get(0));
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
        // For each cloudlet, use DCOA metaheuristic to pick the VM
        DCOAMapper dcoa = new DCOAMapper(vms);
        for (Cloudlet c : cloudlets) {
            c.setVm(vms.get(dcoa.findBestVmIndex(c)));
        }
    }
    private static void qlearningMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
    	QLearningMapper qlMapper = new QLearningMapper(vms);
    	qlMapper.map(cloudlets);
    }
    private static void psosaMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        PSOSAMapper psosa = new PSOSAMapper(vms);
        for (Cloudlet c : cloudlets) {
            c.setVm(vms.get(psosa.findBestVmIndex(c)));
        }
    }

    private static void hybridPsosaMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        HybridPSOSAMapper hybrid = new HybridPSOSAMapper(vms);
        for (Cloudlet c : cloudlets) {
            c.setVm(vms.get(hybrid.findBestVmIndex(c)));
        }
    }

    // --- Result collector ---
    private static SimulationResult collectResults(String name, DatacenterBroker broker, Datacenter dc) {
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        SimulationResult res = new SimulationResult(name);
        double makespan = finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0.0);
        res.setMakespan(makespan);
        double totalWaiting = finished.stream().mapToDouble(c -> c.getStartTime() - c.getSubmissionDelay()).sum();
        res.setAvgWaitingTime(finished.isEmpty() ? 0.0 : totalWaiting / finished.size());
        double totalEnergy = dc.getHostList().stream()
                .mapToDouble(host -> host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean()))
                .sum();
        res.setTotalEnergyConsumption(totalEnergy);
        res.setFinishedCloudlets(finished.size());
        return res;
    }

    // --- Interfaces for mapping strategies ---
    private interface BrokerFactory { DatacenterBroker create(CloudSimPlus sim); }
    private interface StaticMapper { void map(List<Cloudlet> cloudlets, List<Vm> vms); }
}

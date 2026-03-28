package org.cloudsimplus.examples;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
import org.cloudsimplus.utilizationmodels.UtilizationModelFull;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * A minimal but organized, structured and re-usable CloudSim Plus example
 * which shows good coding practices for creating simulation scenarios.
 *
 * <p>It defines a set of constants that enables a developer
 * to change the number of Hosts, VMs and Cloudlets to create
 * and the number of {@link Pe}s for Hosts, VMs and Cloudlets.</p>
 *
 * @author Manoel Campos da Silva Filho
 * @since CloudSim Plus 1.0
 */
public class BasicFirstExample2 {
    private static final int HOSTS = 4;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 1000; // Million Instructions per Second (MIPS)
    private static final int HOST_RAM = 16384; // Increased to 16GB
    private static final long HOST_BW = 100000000L; // in Megabits/s
    private static final long HOST_STORAGE = 1000000; // in Megabytes

    private static final int VMS = 8; // Increased from 2 to 4 to better handle 40 cloudlets
    private static final int VM_PES = 2;

    private static final int CLOUDLETS = 50;
    private static final int CLOUDLET_PES = 2;
    private static final long CLOUDLET_LENGTH = 1000; // Reduced from 10,000 to 5000 MI for testing

    private final CloudSimPlus simulation;
    private final DatacenterBroker broker0;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private Datacenter datacenter0;

    public static void main(String[] args) {
        new BasicFirstExample2();
    }

    private BasicFirstExample2() {
        /*Enables just some level of log messages.
          Make sure to import org.cloudsimplus.util.Log;*/
        //Log.setLevel(ch.qos.logback.classic.Level.WARN);

        simulation = new CloudSimPlus();
        // Set minimum time between events to avoid missing small time increments
        datacenter0 = createDatacenter();
        // Set scheduling interval to a balanced value for frequent event processing
        datacenter0.setSchedulingInterval(0.5);

        // Creates a broker that is a software acting on behalf of a cloud customer to manage his/her VMs and Cloudlets
        broker0 = new DatacenterBrokerSimple(simulation);
        // Significantly increase VM destruction delay to ensure cloudlets finish
        broker0.setVmDestructionDelay(500.0);

        vmList = createVms();
        cloudletList = createCloudlets();
        broker0.submitVmList(vmList);
        broker0.submitCloudletList(cloudletList);
        // Extend simulation time further to ensure all cloudlets complete
        simulation.terminateAt(2000.0);

        
        simulation.start();

        // Check and display the number of finished cloudlets
        final var cloudletFinishedList = broker0.getCloudletFinishedList();
        System.out.println("Number of finished cloudlets: " + cloudletFinishedList.size());
        new CloudletsTableBuilder(cloudletFinishedList).build();
    }

    /**
     * Creates a Datacenter and its Hosts.
     */
    private Datacenter createDatacenter() {
        final var hostList = new ArrayList<Host>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            final var host = createHost();
            hostList.add(host);
        }

        // Uses a VmAllocationPolicySimple by default to allocate VMs
        return new DatacenterSimple(simulation, hostList);
    }

    private Host createHost() {
        final var peList = new ArrayList<Pe>(HOST_PES);
        // List of Host's CPUs (Processing Elements, PEs)
        for (int i = 0; i < HOST_PES; i++) {
            // Uses a PeProvisionerSimple by default to provision PEs for VMs
            peList.add(new PeSimple(HOST_MIPS));
        }

        /*
        Uses ResourceProvisionerSimple by default for RAM and BW provisioning
        and VmSchedulerSpaceShared for VM scheduling.
        */
        return new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
    }

    /**
     * Creates a list of VMs.
     */
    private List<Vm> createVms() {
        final var vmList = new ArrayList<Vm>(VMS);
        for (int i = 0; i < VMS; i++) {
            // Uses a CloudletSchedulerTimeShared by default to schedule Cloudlets
            final var vm = new VmSimple(HOST_MIPS, VM_PES);
            vm.setRam(1024).setBw(100000).setSize(1000).setCloudletScheduler(new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }

        return vmList;
    }

    /**
     * Creates a list of Cloudlets.
     */
    private List<Cloudlet> createCloudlets() {
        final var cloudletList = new ArrayList<Cloudlet>(CLOUDLETS);

        // UtilizationModel defining the Cloudlets use only 50% of any resource all the time
        final var utilizationModel = new UtilizationModelDynamic(0.5);
        final var utilizationModelDynamic = new UtilizationModelDynamic(0.1);
        final var utilizationModelFull = new UtilizationModelFull();
        for (int i = 0; i < CLOUDLETS; i++) {
            final var cloudlet = new CloudletSimple(CLOUDLET_LENGTH, CLOUDLET_PES, utilizationModel);
            cloudlet.setFileSize(300);
            cloudlet.setOutputSize(300);
            cloudlet.setUtilizationModelCpu(utilizationModelFull);
            cloudlet.setUtilizationModelBw(utilizationModelDynamic);
            cloudlet.setUtilizationModelBw(utilizationModelDynamic);
            cloudletList.add(cloudlet);
        }

        return cloudletList;
    }
}

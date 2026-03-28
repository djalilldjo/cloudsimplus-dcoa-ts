package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A custom Broker that implements Shortest Job First (SJF) scheduling policy.
 * It attempts to map Cloudlets with shorter lengths to VMs first.
 * For dynamic cloudlets, it will attempt to find the best VM at submission time.
 */
public class SjfBroker extends DatacenterBrokerSimple {

    public SjfBroker(CloudSimPlus simulation) {
        super(simulation);
    }

    @Override
    protected Vm defaultVmMapper(final Cloudlet cloudlet) {
        final List<Vm> vmList = getVmExecList();
        if (vmList.isEmpty()) {
            throw new IllegalStateException("No VMs available to map cloudlets.");
        }

        // SJF logic: Find the VM that can finish the cloudlet fastest
        // This is a simplistic SJF, as it doesn't re-order the entire cloudlet queue,
        // but rather tries to find the best VM for the *current* cloudlet.
        // A more robust SJF would require sorting the *incoming* cloudlet list
        // and preempting if necessary, which is complex for a broker's defaultVmMapper.
        // For simplicity and comparability within the broker's mapping context,
        // we prioritize the cloudlet that will finish earliest on an available VM.

        // Sort VMs by their current expected finish time for the given cloudlet
        // (assuming no other cloudlets are added to this VM during this decision)
        Optional<Vm> bestVm = vmList.stream()
            .min(Comparator.comparingDouble(vm -> calculateExpectedFinishTime(cloudlet, vm)));

        return bestVm.orElse(vmList.get(0)); // Fallback to the first VM if none found
    }

    /**
     * Estimates the finish time of a Cloudlet on a given VM.
     * This is a simplified calculation and doesn't account for other
     * Cloudlets already scheduled on the VM's scheduler.
     */
    private double calculateExpectedFinishTime(Cloudlet cloudlet, Vm vm) {
        if (vm.getPesNumber() == 0 || vm.getMips() == 0) {
            return Double.MAX_VALUE; // Avoid division by zero
        }
        // Approximate time for the cloudlet to run on this VM's MIPS
        double estimatedExecutionTime = cloudlet.getLength() / (vm.getMips() * vm.getPesNumber());
        // Add current busy time of the VM (simplistic)
        double vmBusyTime = vm.getCloudletScheduler().getCloudletWaitingList().stream()
            .mapToDouble(cl -> cl.getCloudletLength() / (cl.getPesNumber() * cl.getLastAllocatedMips())) // Estimate remaining time for waiting cloudlets
            .sum();
        return vmBusyTime + estimatedExecutionTime;
    }
}
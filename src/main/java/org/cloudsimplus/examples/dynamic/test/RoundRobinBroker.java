package org.cloudsimplus.examples.dynamic.test;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.List;

/**
 * A custom Broker that implements Round Robin scheduling policy.
 * It assigns Cloudlets to VMs in a cyclic manner.
 */
public class RoundRobinBroker extends DatacenterBrokerSimple {

    private int lastVmIndex = 0;

    public RoundRobinBroker(CloudSimPlus simulation) {
        super(simulation);
    }

    @Override
    protected Vm defaultVmMapper(final Cloudlet cloudlet) {
        final List<Vm> vmList = getVmExecList();
        if (vmList.isEmpty()) {
            throw new IllegalStateException("No VMs available to map cloudlets.");
        }

        // Round Robin logic
        final Vm selectedVm = vmList.get(lastVmIndex);
        lastVmIndex = (lastVmIndex + 1) % vmList.size(); // Move to the next VM

        return selectedVm;
    }
}
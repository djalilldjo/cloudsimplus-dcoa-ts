package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.vms.Vm;

@FunctionalInterface
public interface EnergyEstimator {
    /**
     * Estimates the total energy consumed by the given Cloudlet running on the given Vm.
     * @param cloudlet The cloudlet
     * @param vm The VM
     * @return Estimated energy in Joules
     */
    double estimateEnergy(Cloudlet cloudlet, Vm vm);
}

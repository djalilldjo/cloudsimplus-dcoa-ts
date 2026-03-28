package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;

public class PrioritizedCloudlet extends CloudletSimple {
    private double deadline;
    public PrioritizedCloudlet(long length, int pesNumber, UtilizationModelDynamic utilizationModel) {
        super(length, pesNumber, utilizationModel);
        this.deadline = -1;
    }
    public double getDeadline() { return deadline; }
    public void setDeadline(double deadline) { this.deadline = deadline; }
}

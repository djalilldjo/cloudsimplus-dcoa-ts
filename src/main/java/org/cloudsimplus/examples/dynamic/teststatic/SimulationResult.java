package org.cloudsimplus.examples.dynamic.teststatic;

public class SimulationResult {
    private String algorithmName;
    private double makespan;
    private double totalEnergyConsumption;
    private double avgWaitingTime;
    private int finishedCloudlets;
    public SimulationResult(String algorithmName) { this.algorithmName = algorithmName; }
    public void setMakespan(double makespan) { this.makespan = makespan; }
    public void setTotalEnergyConsumption(double totalEnergyConsumption) { this.totalEnergyConsumption = totalEnergyConsumption; }
    public void setAvgWaitingTime(double avgWaitingTime) { this.avgWaitingTime = avgWaitingTime; }
    public void setFinishedCloudlets(int finishedCloudlets) { this.finishedCloudlets = finishedCloudlets; }
    @Override
    public String toString() {
        return String.format("--- %s Results ---\nMakespan: %.2f s\nTotal Energy: %.2f J\nAvg Waiting: %.2f s\nFinished Cloudlets: %d\n",
                algorithmName, makespan, totalEnergyConsumption, avgWaitingTime, finishedCloudlets);
    }
}


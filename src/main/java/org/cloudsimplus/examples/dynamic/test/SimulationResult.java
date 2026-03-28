package org.cloudsimplus.examples.dynamic.test;

import java.util.List;

/**
 * A class to hold the simulation results for a specific scheduling algorithm.
 */
public class SimulationResult {
    private String algorithmName;
    private double makespan;
    private double totalEnergyConsumption;
    private double avgWaitingTime;
    private int slaViolations;
    private int finishedCloudlets;
    private double totalExecutionTime; // For average waiting time calculation

    public SimulationResult(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    // Getters and Setters
    public String getAlgorithmName() { return algorithmName; }
    public void setAlgorithmName(String algorithmName) { this.algorithmName = algorithmName; }
    public double getMakespan() { return makespan; }
    public void setMakespan(double makespan) { this.makespan = makespan; }
    public double getTotalEnergyConsumption() { return totalEnergyConsumption; }
    public void setTotalEnergyConsumption(double totalEnergyConsumption) { this.totalEnergyConsumption = totalEnergyConsumption; }
    public double getAvgWaitingTime() { return avgWaitingTime; }
    public void setAvgWaitingTime(double avgWaitingTime) { this.avgWaitingTime = avgWaitingTime; }
    public int getSlaViolations() { return slaViolations; }
    public void setSlaViolations(int slaViolations) { this.slaViolations = slaViolations; }
    public int getFinishedCloudlets() { return finishedCloudlets; }
    public void setFinishedCloudlets(int finishedCloudlets) { this.finishedCloudlets = finishedCloudlets; }
    public double getTotalExecutionTime() { return totalExecutionTime; }
    public void setTotalExecutionTime(double totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }

    @Override
    public String toString() {
        return String.format(
            "--- %s Results ---\n" +
            "Makespan: %.2f seconds\n" +
            "Total Energy Consumption: %.2f Joules\n" +
            "Average Waiting Time: %.2f seconds\n" +
            "SLA Violations: %d\n" +
            "Finished Cloudlets: %d\n",
            algorithmName, makespan, totalEnergyConsumption, avgWaitingTime, slaViolations, finishedCloudlets
        );
    }
}
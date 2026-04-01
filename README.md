# Dynamic Cloud Mapping and Scheduling Simulation (CloudSim Plus)

This repository contains a comprehensive suite of cloud scheduling algorithms evaluated using the **CloudSim Plus** framework. It includes the implementation, configuration, and execution pipeline to compare various meta-heuristic, heuristic, and Reinforcement Learning (RL) approaches for dynamic resource allocation in a heterogeneous cloud computing environment.

## Algorithms Evaluated

The core comparison evaluates **DCOA-TS** (Discrete Coati Optimization Algorithm) and **DQN** (Deep Q-Network) against several other baseline and state-of-the-art scheduling approaches, including:
- **Meta-Heuristics**: PSO-SA (Particle Swarm Optimization + Simulated Annealing), DTSO-TS, DGWO, GO, DWOA, DBDE, I-COA
- **Machine Learning**: DQN Mapper
- **Baselines**: FCFS (First-Come, First-Served), SJF (Shortest Job First), Round Robin

The primary metrics assessed during these comparisons are **Makespan**, **Energy Consumption**, **Average Waiting Time**, **Mapping Time**, and **Deadline Miss Rates**.

---

## Project Structure

The primary simulation implementations are located in:
`src/main/java/org/cloudsimplus/examples/dynamic/teststaticglobalmaper/`

### Key Files:
- `StaticSimulationRunnerHeterogen.java`: The main runner logic for executing all scenarios (Base Statistical/Overhead, Weight Sensitivity, Parameter Sensitivity, and Deadline Experiments).

## How to Run the Simulations

The project requires **JDK 17+** and utilizes **Maven** for dependency management and execution. You do not need to install CloudSim Plus manually; Maven will automatically download all required JAR libraries.

### 1. Full Evaluation Suite
To execute the comprehensive algorithms evaluation and generate the primary datasets (e.g., `raw_runs.csv`, `weight_sensitivity.csv`, `param_sensitivity.csv`, `deadline_results.csv`):

```bash
# 1. Clean and Compile the project
mvn clean compile

# 2. Run the main evaluation runner
mvn exec:java -Dexec.mainClass="org.cloudsimplus.examples.dynamic.teststaticglobalmaper.StaticSimulationRunnerHeterogen"
```

---

## Simulation Configuration

The configuration parameters for Hosts, VMs, Cloudlets, and the schedulers can be found directly inside the runner implementations (such as `StaticSimulationRunnerHeterogen.java`).

### Default Setup
- **Workloads**: Tests are evaluated against 6 incremental workload sizes originating from `cloudlets.csv` (e.g., 50, 100, 500, 1000, 2000, 5000 cloudlets).
- **Heterogeneous Environment**: 
  - **Hosts**: 4 varied host configurations (RAM: 4GB - 64GB, MIPS: 750 - 3500, Power: 20W - 92W max).
  - **VMs**: 16 varied VM flavors to simulate a highly heterogeneous compute environment (RAM: 1GB - 16GB, PES: 1 - 4).
- **Randomization Seeds**: The framework uses up to 30 deterministic randomization seeds (`SEEDS = [1000 ... 1029]`) to ensure statistical significance across iterative evaluations.

### Weight Customization
The fitness/objective function incorporates a weighted evaluation of Energy, Makespan, and Deadline Violations:
```java
config.w_energy = 0.2; 
config.w_makespan = 0.7; 
config.w_violation = 0.1;
```
To test custom environments, you can alter these configurations inside the `runWeightSensitivity()` method.

---

## Outputs and Logging

To optimize mapping time execution speed and save massive amounts of CLI output, the `CloudSim Plus` runtime logs are turned off by default (`Log.setLevel(Level.OFF)`).

All generated output is saved to standard CSV files in the project root. The Python script plot.py can then be run to generate the final plots from the findings.

package org.cloudsimplus.examples.dynamic.teststaticglobalmaper;

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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.cloudsimplus.util.Log;
import ch.qos.logback.classic.Level;

public class StaticSimulationRunnerHeterogen {

    private static final int HOSTS = 4;
    private static final int VMS = 16;
    private static final int CLOUDLET_PES = 1;
    private static final String CLOUDLET_CSV = "cloudlets.csv";

    // Host Config
    private static final int[] HOST_RAM = {16384, 4096, 65536, 32768};
    private static final int[] HOST_PES = {16, 4, 32, 8};
    private static final int[] HOST_MIPS = {2000, 750, 3500, 1200};
    private static final long[] HOST_BW = {100_000_000L, 10_000_000L, 400_000_000L, 50_000_000L};
    private static final long[] HOST_STORAGE = {2_000_000L, 300_000L, 8_000_000L, 1_000_000L};
    private static final double[] STATIC_POWER = {30, 20, 60, 40};
    private static final int[] MAX_POWER = {48, 36, 92, 55};

    // VM Config
    private static final int[] VM_RAM = {1024,2048,4096,6144,8192,3072,16384,2048,4096,4096,2048,8192,1024,8192,4096,2048};
    private static final int[] VM_PES = {1, 2, 4, 4, 1, 4, 2, 4, 4, 1, 2, 4, 2, 4, 2, 4};
    private static final int[] VM_MIPS = {700,1200,2300,3300,1100,2100,3500,1000,1800,2600,900,3400,1300,2500,2200,800};
    private static final long[] VM_BW = {10000,20000,50000,90000,25000,35000,200000,15000,40000,80000,12000,150000,15000,25000,75000,10000};
    private static final long[] VM_SIZE = {3000,5000,8000,12000,16000,6000,20000,4000,7000,9000,5000,18000,3000,16000,8000,4000};

    private static final int[] WORKLOAD_SIZES = {50, 100, 500, 1000, 2000, 5000};
    private static final long[] SEEDS = new long[30]; // 30 seeds
    static {
        for (int i = 0; i < 30; i++) SEEDS[i] = 1000 + i;
    }
    
    // Experiment filenames
    private static final String CSV_RAW = "raw_runs.csv";
    private static final String CSV_SUMMARY = "summary_by_algorithm.csv";
    private static final String CSV_WEIGHTS = "weight_sensitivity.csv";
    private static final String CSV_DEADLINES = "deadline_results.csv";
    private static final String CSV_RUNTIME = "runtime_overhead.csv";
    private static final String CSV_PARAMS = "param_sensitivity.csv";
    private static final String JSON_MANIFEST = "run_manifest.json";

    public static void main(String[] args) {
        Log.setLevel(Level.OFF); // Disable CloudSim Plus logging to save execution time
        System.out.println("Starting Manuscript Experiments Generation...");
        cleanOutputs();
        writeManifest();

        System.out.println("1. Running Base Statistical & Overhead Experiments...");
        runBaseExperiment();

        System.out.println("2. Running Weight Sensitivity Experiments...");
        runWeightSensitivity();

        System.out.println("3. Running Parameter Sensitivity Experiments...");
        runParameterSensitivity();

        System.out.println("4. Running Deadline Experiments...");
        runDeadlineExperiment();

        System.out.println("5. Generating Summaries...");
        generateSummaryByAlgorithm();

        System.out.println("All Done!");
        appendCsvLine("SIMULATION_COMPLETED.txt", "ALL SCENARIOS COMPLETED SUCCESSFULLY AT: " + new java.util.Date());
    }

    private static void cleanOutputs() {
        try {
            Files.deleteIfExists(Paths.get(CSV_RAW));
            Files.deleteIfExists(Paths.get(CSV_SUMMARY));
            Files.deleteIfExists(Paths.get(CSV_WEIGHTS));
            Files.deleteIfExists(Paths.get(CSV_DEADLINES));
            Files.deleteIfExists(Paths.get(CSV_RUNTIME));
            Files.deleteIfExists(Paths.get(CSV_PARAMS));
            Files.deleteIfExists(Paths.get(JSON_MANIFEST));
        } catch (IOException ignored) {}
    }

    private static void writeManifest() {
        String json = "{\n" +
            "  \"repository\": \"cloudsimplus-examplesall\",\n" +
            "  \"main_class\": \"StaticSimulationRunnerHeterogen\",\n" +
            "  \"algorithms_evaluated\": [\"FCFS\", \"RoundRobin\", \"SJF\", \"PSO-SA\", \"DTSO-TS\", \"DCOA-TS\", \"DQN\", \"DGWO\", \"GO\", \"DWOA\", \"DBDE\", \"I-COA\"],\n" +
            "  \"seeds_used\": " + Arrays.toString(SEEDS) + ",\n" +
            "  \"workload_file\": \"" + CLOUDLET_CSV + "\",\n" +
            "  \"workload_sizes\": " + Arrays.toString(WORKLOAD_SIZES) + "\n" +
            "}";
        try (PrintWriter pw = new PrintWriter(new FileWriter(JSON_MANIFEST))) {
            pw.println(json);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void runBaseExperiment() {
        String[] algos = {"FCFS", "RoundRobin", "SJF", "PSO-SA", "DTSO-TS", "DCOA-TS", "DQN", "DGWO", "GO", "DWOA", "DBDE", "I-COA"};
        initCsv(CSV_RAW, "algorithm,scenario_name,num_cloudlets,seed,population,iterations,episodes,w_energy,w_makespan,w_violation,makespan_sec,makespan_ms,total_energy_j,avg_waiting_sec,avg_waiting_ms,finished_cloudlets,mapping_time_ms,peak_memory_mb,deadline_miss_rate,avg_tardiness_sec,unit_note");
        initCsv(CSV_RUNTIME, "algorithm,scenario_name,num_cloudlets,seed,mapping_time_ms,peak_memory_mb");

        for (int size : WORKLOAD_SIZES) {
            String scenario = "Scenario_" + size;
            for (String algo : algos) {
                for (long seed : SEEDS) {
                    RunConfig config = new RunConfig(algo, scenario, size, seed);
                    config.w_energy = 0.2; config.w_makespan = 0.7; config.w_violation = 0.1;
                    if(algo.equals("DTSO-TS") || algo.equals("PSO-SA") || 
                       algo.equals("DGWO") || algo.equals("GO") || algo.equals("DWOA") || 
                       algo.equals("DBDE") || algo.equals("I-COA")) { config.population=200; config.iterations=100; }
                    if(algo.equals("DCOA-TS")) { config.population=1000; config.iterations=1000; }
                    if(algo.equals("DQN")) { config.episodes=2000; }
                    
                    RunMetrics metrics = executeSimulation(config, false, 0, 0);
                    
                    appendCsvLine(CSV_RAW, metrics.toRawLine());
                    appendCsvLine(CSV_RUNTIME, String.format("%s,%s,%d,%d,%d,%d",
                        algo, scenario, size, seed, metrics.mapping_time_ms, metrics.peak_memory_mb));
                }
            }
        }
    }

    private static void runWeightSensitivity() {
        initCsv(CSV_WEIGHTS, "algorithm,scenario_name,num_cloudlets,seed,w_energy,w_makespan,w_violation,makespan_sec,total_energy_j,avg_waiting_sec");
        double[][] weights = {
            {0.8, 0.1, 0.1},
            {0.45, 0.45, 0.10},
            {0.2, 0.7, 0.1}
        };
        for (int size : WORKLOAD_SIZES) {
            String scenario = "Scenario_" + size;
            for (double[] w : weights) {
                for (long seed : SEEDS) {
                    RunConfig config = new RunConfig("DCOA-TS", scenario, size, seed);
                    config.w_energy = w[0]; config.w_makespan = w[1]; config.w_violation = w[2];
                    config.population=1000; config.iterations=1000;
                    RunMetrics metrics = executeSimulation(config, false, 0, 0);
                    appendCsvLine(CSV_WEIGHTS, String.format("%s,%s,%d,%d,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f",
                        "DCOA-TS", scenario, size, seed, w[0], w[1], w[2],
                        metrics.makespan_sec, metrics.total_energy_j, metrics.avg_waiting_sec));
                }
            }
        }
    }

    private static void runParameterSensitivity() {
        initCsv(CSV_PARAMS, "algorithm,scenario_name,num_cloudlets,seed,population,iterations,makespan_sec,total_energy_j,avg_waiting_sec,mapping_time_ms");
        int[] pops = {10, 24, 50, 100, 500};
        int[] iters = {10, 20, 50, 100, 500};
        int size = 200;
        String scenario = "Scenario_200";
        String[] algos = {"PSO-SA", "DTSO-TS", "DCOA-TS"}; // Algos used in sensitivity analysis section of the paper
        for (String algo : algos) {
            for (int p : pops) {
                for (int i : iters) {
                    for (long seed : SEEDS) {
                        RunConfig config = new RunConfig(algo, scenario, size, seed);
                        config.w_energy = 0.2; config.w_makespan = 0.7; config.w_violation = 0.1;
                        config.population = p; config.iterations = i;
                        RunMetrics metrics = executeSimulation(config, false, 0, 0);
                        appendCsvLine(CSV_PARAMS, String.format("%s,%s,%d,%d,%d,%d,%.4f,%.4f,%.4f,%d",
                            algo, scenario, size, seed, p, i,
                            metrics.makespan_sec, metrics.total_energy_j, metrics.avg_waiting_sec, metrics.mapping_time_ms));
                    }
                }
            }
        }
    }

    private static void runDeadlineExperiment() {
        initCsv(CSV_DEADLINES, "algorithm,scenario_name,num_cloudlets,seed,phi_fraction,rho_tightness,makespan_sec,total_energy_j,deadline_miss_rate,avg_tardiness_sec");
        double[] phis = {0.3, 0.6};
        double[] rhos = {1.2, 1.5, 2.0};
        String[] algos = {"DCOA-TS", "DQN", "FCFS", "DGWO", "GO", "DWOA", "DBDE", "I-COA"}; // Compare meta, rl, and baseline

        for (int size : WORKLOAD_SIZES) {
            String scenario = "Scenario_" + size;
            for (double phi : phis) {
                for (double rho : rhos) {
                    for (String algo : algos) {
                        for(int i=0; i<5; i++) { // only 5 seeds for deadlines to save time
                            long seed = SEEDS[i];
                            RunConfig config = new RunConfig(algo, scenario, size, seed);
                            config.w_energy = 0.2; config.w_makespan = 0.5; config.w_violation = 0.3;
                            if(algo.equals("DGWO") || algo.equals("GO") || 
                               algo.equals("DWOA") || algo.equals("DBDE") || algo.equals("I-COA")) { config.population=200; config.iterations=100; }
                            if(algo.equals("DCOA-TS")) { config.population=1000; config.iterations=1000; }
                            if(algo.equals("DQN")) { config.episodes=2000; }
                            
                            RunMetrics m = executeSimulation(config, true, phi, rho);
                            appendCsvLine(CSV_DEADLINES, String.format("%s,%s,%d,%d,%.1f,%.1f,%.4f,%.4f,%.4f,%.4f",
                                algo, scenario, size, seed, phi, rho, m.makespan_sec, m.total_energy_j, m.deadline_miss_rate, m.avg_tardiness_sec));
                        }
                    }
                }
            }
        }
    }

    private static void generateSummaryByAlgorithm() {
        // Read raw_runs.csv and group by algorithm + scenario to compute means.
        // For simplicity, just appending a note. In a real shell process, python pandas is better.
        // We will do a generic simple average.
        initCsv(CSV_SUMMARY, "algorithm,scenario_name,mean_makespan_sec,mean_total_energy_j,mean_mapping_time_ms");
        Map<String, double[]> sums = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_RAW))) {
            String line;
            br.readLine(); // skip header
            while((line = br.readLine()) != null) {
                String[] p = line.split(",");
                String key = p[0] + "," + p[1];
                double mk = Double.parseDouble(p[10]);
                double e = Double.parseDouble(p[12]);
                double mt = Double.parseDouble(p[16]);
                sums.putIfAbsent(key, new double[]{0,0,0});
                counts.putIfAbsent(key, 0);
                double[] s = sums.get(key);
                s[0] += mk; s[1] += e; s[2] += mt;
                counts.put(key, counts.get(key) + 1);
            }
        } catch (Exception e) {}

        for (String key : sums.keySet()) {
            double[] s = sums.get(key);
            int c = counts.get(key);
            appendCsvLine(CSV_SUMMARY, String.format("%s,%.4f,%.4f,%.1f", key, s[0]/c, s[1]/c, s[2]/c));
        }
    }

    // ======================================
    //     SIMULATION CORE EXECUTION
    // ======================================

    public static class RunConfig {
        String algo; String scenario; int cloudlets; long seed;
        int population=0, iterations=0, episodes=0;
        double w_energy=0, w_makespan=0, w_violation=0;
        public RunConfig(String a, String sc, int cl, long sd){ algo=a; scenario=sc; cloudlets=cl; seed=sd; }
    }

    public static class RunMetrics {
        RunConfig config;
        double makespan_sec, makespan_ms, total_energy_j, avg_waiting_sec, avg_waiting_ms;
        int finished_cloudlets; long mapping_time_ms; long peak_memory_mb;
        double deadline_miss_rate, avg_tardiness_sec;
        String unit_note = "ms and sec explicitly named";

        public String toRawLine() {
            return String.format("%s,%s,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%.4f,%.4f,%s",
                config.algo, config.scenario, config.cloudlets, config.seed,
                config.population, config.iterations, config.episodes,
                config.w_energy, config.w_makespan, config.w_violation,
                makespan_sec, makespan_ms, total_energy_j, avg_waiting_sec, avg_waiting_ms,
                finished_cloudlets, mapping_time_ms, peak_memory_mb,
                deadline_miss_rate, avg_tardiness_sec, unit_note);
        }
    }

    private static RunMetrics executeSimulation(RunConfig config, boolean withDeadlines, double phi, double rho) {
        CloudSimPlus simulation = new CloudSimPlus();
        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(simulation);
        Datacenter datacenter = createDatacenter(simulation);
        List<Vm> vms = createVms();
        List<Cloudlet> cloudlets = loadCloudlets(config, withDeadlines, phi, rho, vms);

        EnergyEstimator estimator = StaticSimulationRunnerHeterogen::estimateEnergyPreSim;

        long memBefore = getUsedMemoryMb();
        long startMapping = System.currentTimeMillis();

        if (config.algo.equals("FCFS")) fcfsMapping(cloudlets, vms);
        else if (config.algo.equals("RoundRobin")) roundRobinMapping(cloudlets, vms);
        else if (config.algo.equals("SJF")) sjfMapping(cloudlets, vms);
        else if (config.algo.equals("DTSO-TS")) {
            int[] mapping = new DTSOMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("PSO-SA")) {
            int[] mapping = new PSOSAMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("DCOA-TS")) {
            DCOAMapper mapper = new DCOAMapper(vms, config.population, config.iterations, config.seed, estimator);
            mapper.setWeights(config.w_energy, config.w_makespan, config.w_violation);
            int[] mapping = mapper.findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("DGWO")) {
            int[] mapping = new DGWOMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("GO")) {
            int[] mapping = new GOMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("DWOA")) {
            int[] mapping = new DWOAMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("DBDE")) {
            int[] mapping = new DBDEMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("I-COA")) {
            int[] mapping = new ICOAMapper(vms, config.population, config.iterations, config.seed, estimator).findBestMapping(cloudlets, vms);
            for(int i=0;i<cloudlets.size();i++) cloudlets.get(i).setVm(vms.get(mapping[i]));
        } else if (config.algo.equals("DQN")) {
            new DQNMapper(vms, config.episodes, 0.1, 0.9, 1.0, 0.01, 0.995, config.seed, estimator).map(cloudlets);
        }

        long mappingTimeMs = System.currentTimeMillis() - startMapping;
        long memAfter = getUsedMemoryMb();

        broker.submitVmList(vms);
        broker.submitCloudletList(cloudlets);
        datacenter.setSchedulingInterval(0.5);
        broker.setVmDestructionDelay(20000);
        
        simulation.start();

        return collectResults(config, broker, datacenter, cloudlets, mappingTimeMs, Math.max(0, memAfter - memBefore));
    }

    private static double estimateEnergyPreSim(Cloudlet cl, Vm vm) {
        double execTime = cl.getLength() / (vm.getMips() * vm.getPesNumber());
        // explicit and consistent assumption: 50W static overhead proxy
        return execTime * 50.0; 
    }

    private static void initCsv(String file, String header) {
        if (!new File(file).exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) { pw.println(header); }
            catch (IOException ignored) {}
        }
    }

    private static void appendCsvLine(String file, String line) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) { pw.println(line); }
        catch (IOException ignored) {}
    }

    private static long getUsedMemoryMb() {
        System.gc(); // optional, but helps accurate tracking
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    private static List<Cloudlet> loadCloudlets(RunConfig config, boolean withDeadlines, double phi, double rho, List<Vm> vms) {
        double maxVmSpeed = vms.stream().mapToDouble(v -> v.getMips() * v.getPesNumber()).max().orElse(1);
        double minVmSpeed = vms.stream().mapToDouble(v -> v.getMips() * v.getPesNumber()).min().orElse(1);
        double avgVmSpeed = (maxVmSpeed + minVmSpeed) / 2.0;

        var cpuUtil = new UtilizationModelFull();
        var util = new UtilizationModelDynamic(0.2);
        List<Cloudlet> list = new ArrayList<>();
        Random rand = new Random(config.seed);

        try (BufferedReader br = new BufferedReader(new FileReader(CLOUDLET_CSV))) {
            String line;
            boolean first = true;
            int count = 0;
            while (((line = br.readLine()) != null) && count < config.cloudlets) {
                if (first) { first = false; continue; }
                String[] parts = line.split(";");
                long length = Long.parseLong(parts[1]);
                
                Cloudlet cl;
                if (withDeadlines) {
                    PrioritizedCloudlet pcl = new PrioritizedCloudlet(length, CLOUDLET_PES, util);
                    if (rand.nextDouble() < phi) {
                        // deadline = exec time on avg VM * rho
                        double estTime = length / avgVmSpeed;
                        pcl.setDeadline(estTime * rho);
                    }
                    cl = pcl;
                } else {
                    cl = new CloudletSimple(length, CLOUDLET_PES);
                    cl.setUtilizationModelRam(util);
                    cl.setUtilizationModelBw(util);
                }
                cl.setFileSize(Long.parseLong(parts[2]));
                cl.setOutputSize(Long.parseLong(parts[3]));
                cl.setUtilizationModelCpu(cpuUtil);
                
                list.add(cl);
                count++;
            }
        } catch (IOException e) { e.printStackTrace(); }
        return list;
    }

    private static Datacenter createDatacenter(CloudSimPlus sim) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < HOST_PES[i]; j++) peList.add(new PeSimple(HOST_MIPS[i]));
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
                    .setRam(VM_RAM[i]).setBw(VM_BW[i]).setSize(VM_SIZE[i])
                    .setCloudletScheduler(new CloudletSchedulerSpaceShared());
            vm.enableUtilizationStats();
            vms.add(vm);
        }
        return vms;
    }

    private static RunMetrics collectResults(RunConfig config, DatacenterBrokerSimple broker, Datacenter datacenter, List<Cloudlet> expected, long mapTime, long memSize) {
        List<Cloudlet> finished = broker.getCloudletFinishedList();
        RunMetrics r = new RunMetrics();
        r.config = config;
        r.mapping_time_ms = mapTime;
        r.peak_memory_mb = memSize;
        r.finished_cloudlets = finished.size();

        r.makespan_sec = finished.stream().mapToDouble(Cloudlet::getFinishTime).max().orElse(0);
        r.makespan_ms = r.makespan_sec * 1000;
        
        double totalW = finished.stream().mapToDouble(c -> c.getStartTime() - c.getSubmissionDelay()).sum();
        r.avg_waiting_sec = finished.isEmpty() ? 0 : totalW / finished.size();
        r.avg_waiting_ms = r.avg_waiting_sec * 1000;

        r.total_energy_j = datacenter.getHostList().stream().mapToDouble(host -> 
            host.getPowerModel().getPower(host.getCpuUtilizationStats().getMean()) * r.makespan_sec
        ).sum();

        int misses = 0;
        double tardinessSum = 0;
        int deadlineCount = 0;

        for (Cloudlet c : finished) {
            if (c instanceof PrioritizedCloudlet) {
                PrioritizedCloudlet pc = (PrioritizedCloudlet) c;
                if (pc.getDeadline() > 0) {
                    deadlineCount++;
                    if (c.getFinishTime() > pc.getDeadline()) {
                        misses++;
                        tardinessSum += (c.getFinishTime() - pc.getDeadline());
                    }
                }
            }
        }
        
        r.deadline_miss_rate = deadlineCount == 0 ? 0 : (double) misses / deadlineCount;
        r.avg_tardiness_sec = misses == 0 ? 0 : tardinessSum / misses;

        return r;
    }

    // Baselines
    private static void fcfsMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        for (int i = 0; i < cloudlets.size(); i++) cloudlets.get(i).setVm(vms.get(i % vms.size()));
    }
    private static void roundRobinMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        List<Vm> sortedVms = new ArrayList<>(vms);
        sortedVms.sort((a, b) -> Double.compare(b.getMips(), a.getMips()));
        for (int i = 0; i < cloudlets.size(); i++) cloudlets.get(i).setVm(sortedVms.get(i % sortedVms.size()));
    }
    private static void sjfMapping(List<Cloudlet> cloudlets, List<Vm> vms) {
        cloudlets.sort(Comparator.comparingLong(Cloudlet::getLength));
        for (int i = 0; i < cloudlets.size(); i++) cloudlets.get(i).setVm(vms.get(i % vms.size()));
    }
}

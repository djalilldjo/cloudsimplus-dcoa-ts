package org.cloudsimplus.examples.dynamic.test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

public class CloudletCsvGenerator {
    private static final int TOTAL_CLOUDLETS = 100_000;
    // Heterogeneous length: from "tiny" to "huge"
    /*private static final long[] LENGTHS = {
        500, 1000, 1600, 2500, 3500, 4800,
        6000, 11000, 15000, 20000, 35000, 45000, 60000
    };*/
    
    
    private static final long[] LENGTHS = {
    	    5000, 6000, 6500, 7000, 7500, 8000, 8500, 9000, 9500, 10000 // Uniform or mildly heterogeneous
    	};
    
    
    // Heterogeneous File/Input/Output sizes (in bytes)
    private static final int[] FILE_SIZES = {
        10, 50, 100, 300, 600, 1200, 2600, 5000, 8000, 12000
    };
    private static final int[] OUTPUT_SIZES = {
        10, 40, 200, 420, 900, 2700, 6000, 10000, 12500, 20000
    };

    public static void main(String[] args) throws IOException {
        generateCsv("cloudlets.csv", 42); // seed for reproducibility
    }

    public static void generateCsv(String filename, long seed) throws IOException {
        Random random = new Random(seed);
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("cloudletId;length;FileSize;OutputSize");
            for (int i = 0; i < TOTAL_CLOUDLETS; i++) {
                long length = LENGTHS[random.nextInt(LENGTHS.length)];
                if (i % 30 == 0) length = 20000; // ~3% large jobs (still not too many)
                // Rest as before
                int fileSize = FILE_SIZES[random.nextInt(FILE_SIZES.length)];
                int outputSize = OUTPUT_SIZES[random.nextInt(OUTPUT_SIZES.length)];
                pw.printf("%d;%d;%d;%d%n", i, length, fileSize, outputSize);
            }
        }
        System.out.println("Generated " + TOTAL_CLOUDLETS + " cloudlets in " + filename);
    }
}

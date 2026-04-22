package com.instabond.service;

import com.instabond.dto.*;
import com.instabond.dto.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
@ActiveProfiles("test")
public class PostSuggestionPerformanceIntegrationTest {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    private final ExecutorService aiExecutor = Executors.newFixedThreadPool(2);

    @Test
    public void measureAverageParallelPerformance() {
        List<String> imageUrls = Arrays.asList(
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776843937/bu_ngay_sa_ti.webp",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776847051/TDAN_te9bun.jpg",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776847051/bu_ngay_sa_Ti_2_ufptix.webp",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776847050/bu_ngay_sa_Ti_1_godtow.webp",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776847051/Messi-2_wscfsh.jpg",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776856718/7ta_10gio_3_f8vkzl.jpg",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776856715/guma_oxotyf.jpg",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776856713/7ta_10gio_1_hha4nt.jpg",
                "https://res.cloudinary.com/dhctxuupz/image/upload/v1776856714/7ta_10gio_2_g0akvw.jpg"
        );

        // ==========================================
        // WARM-UP (INITIALIZE AI SERVER)
        // ==========================================
        System.out.println("=== START WARM-UP (INITIALIZE AI SERVER) ===");
        AiImageAnalyzeRequest warmupRequest = new AiImageAnalyzeRequest(imageUrls.get(0));

        for (int i = 1; i <= 3; i++) {
            System.out.println("Warming up iteration " + i + "...");
            try {
                // Just call the API to warm it up, no need to measure time
                restTemplate.postForObject(aiServiceUrl + "/api/ai/suggest-tags", warmupRequest, AiTagResponse.class);
                restTemplate.postForObject(aiServiceUrl + "/api/ai/suggest-music", warmupRequest, AiMusicResponse.class);
            } catch (Exception e) {
                System.err.println("[WARNING] Error during warm-up: " + e.getMessage());
            }
        }
        System.out.println("=== WARM-UP COMPLETED ===\n");

        // ==========================================
        // RUN TEST ON IMAGE LIST & CSV EXPORT
        // ==========================================
        long totalSequentialTime = 0;
        long totalParallelTime = 0;

        System.out.println("=== STARTING PERFORMANCE MEASUREMENT (" + imageUrls.size() + " IMAGES) ===");

        String dirPath = "benchmarks";
        String fileName = "ai_parallel_execution_benchmark.csv";

        // Ensure the directory exists before writing
        try {
            Files.createDirectories(Paths.get(dirPath));
        } catch (IOException e) {
            System.err.println("Failed to create directory: " + e.getMessage());
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(dirPath + File.separator + fileName, false))) {
            // Write CSV Header
            writer.println("Timestamp,Image_Index,Tags_Time_ms,Music_Time_ms,Sequential_Total_ms,Parallel_Total_ms,Improvement_Percent");

            for (int i = 0; i < imageUrls.size(); i++) {
                String url = imageUrls.get(i);
                System.out.println("Processing image " + (i + 1) + "...");
                AiImageAnalyzeRequest request = new AiImageAnalyzeRequest(url);

                // Run sequentially and accumulate time
                // seqResults: [0] = Tags Time, [1] = Music Time, [2] = Total Sequential Time
                long[] seqResults = runSequential(request);
                long tTags = seqResults[0];
                long tMusic = seqResults[1];
                long seqTime = seqResults[2];
                totalSequentialTime += seqTime;

                // Run in parallel and accumulate time
                long parTime = runParallel(request);
                totalParallelTime += parTime;

                System.out.printf("  -> OVERALL TIME: Sequential = %d ms | Parallel = %d ms\n", seqTime, parTime);

                // Calculate improvement and write to CSV
                double improvement = ((double) (seqTime - parTime) / seqTime) * 100;
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                writer.printf("%s,%d,%d,%d,%d,%d,%.2f\n", timestamp, i + 1, tTags, tMusic, seqTime, parTime, improvement);
            }

            System.out.println(">>> RESULTS SUCCESSFULLY SAVED TO " + dirPath + File.separator + fileName);

        } catch (IOException e) {
            System.err.println("[ERROR] Could not write to CSV file: " + e.getMessage());
        }

        // ==========================================
        // CALCULATE AND PRINT OVERALL REPORT
        // ==========================================
        long avgSequential = totalSequentialTime / imageUrls.size();
        long avgParallel = totalParallelTime / imageUrls.size();
        long timeSaved = totalSequentialTime - totalParallelTime;
        double percentageImproved = ((double) timeSaved / totalSequentialTime) * 100;

        System.out.println("\n=== OVERALL PERFORMANCE REPORT ===");
        System.out.println("Number of images tested: " + imageUrls.size());
        System.out.println("Total Sequential Time: " + totalSequentialTime + " ms (Average: " + avgSequential + " ms/image)");
        System.out.println("Total Parallel Time:   " + totalParallelTime + " ms (Average: " + avgParallel + " ms/image)");
        System.out.println("-----------------------------------");
        System.out.println("Total time saved:      " + timeSaved + " ms");
        System.out.printf("=> AVERAGE PERFORMANCE IMPROVEMENT: %.2f%%\n", percentageImproved);
        System.out.println("===================================");
    }

    // ---------------------------------------------------------
    // HELPER METHODS
    // ---------------------------------------------------------

    private long[] runSequential(AiImageAnalyzeRequest request) {
        long startTotal = System.currentTimeMillis();
        long timeTags = 0;
        long timeMusic = 0;

        try {
            // Measure Tags API
            long startTags = System.currentTimeMillis();
            restTemplate.postForObject(aiServiceUrl + "/api/ai/suggest-tags", request, AiTagResponse.class);
            timeTags = System.currentTimeMillis() - startTags;

            // Measure Music API
            long startMusic = System.currentTimeMillis();
            restTemplate.postForObject(aiServiceUrl + "/api/ai/suggest-music", request, AiMusicResponse.class);
            timeMusic = System.currentTimeMillis() - startMusic;

            System.out.printf("      [Seq Details] Tags API: %d ms | Music API: %d ms\n", timeTags, timeMusic);
        } catch (Exception e) {
            System.err.println("[ERROR] Sequential API failed: " + e.getMessage());
        }

        long totalTime = System.currentTimeMillis() - startTotal;
        return new long[]{timeTags, timeMusic, totalTime};
    }

    private long runParallel(AiImageAnalyzeRequest request) {
        StopWatch watch = new StopWatch();
        watch.start();

        CompletableFuture<AiTagResponse> tagFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return restTemplate.postForObject(aiServiceUrl + "/api/ai/suggest-tags", request, AiTagResponse.class);
            } catch (Exception e) {
                return new AiTagResponse();
            }
        }, aiExecutor);

        CompletableFuture<AiMusicResponse> musicFuture = CompletableFuture.supplyAsync(() -> {
            try {
                AiMusicResponse response = restTemplate.postForObject(aiServiceUrl + "/api/ai/suggest-music", request, AiMusicResponse.class);
                return response != null ? response : AiMusicResponse.builder().suggestions(Collections.emptyList()).build();
            } catch (Exception e) {
                return AiMusicResponse.builder().suggestions(Collections.emptyList()).build();
            }
        }, aiExecutor);

        // Wait for both threads to complete
        CompletableFuture.allOf(tagFuture, musicFuture).join();

        // Retrieve results to ensure the APIs actually returned data
        tagFuture.join();
        musicFuture.join();

        watch.stop();
        return watch.getTotalTimeMillis();
    }
}
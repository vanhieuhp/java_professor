package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceAggregatorServiceImpl implements PriceAggregatorService {

    private final List<Supplier> suppliers;
    private final ExecutorService threadPool;

    @Override
    public List<PriceResult> fetchAllPrices(String product) {
        long start = System.currentTimeMillis();

        // Phase 1: fire off all 5 supplier calls simultaneously
        List<Future<PriceResult>> futures = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            Future<PriceResult> future = threadPool.submit(() -> supplier.getPrice(product));
            futures.add(future);
        }

        // At this point, all 5 suppliers are running in background threads
        // This loop itself finishes almost instantly

        // Phase 2: wait for each result (with timeout)
        List<PriceResult> results = new ArrayList<>();
        for (Future<PriceResult> future : futures) {
            try {
                PriceResult result = future.get(3, TimeUnit.SECONDS);
                results.add(result);
            } catch (TimeoutException e) {
                future.cancel(true); // interrupt that supplier's thread
                log.warn("A supplier timed out - skipping");
            } catch (ExecutionException e) {
                log.warn("Supplier threw exception: {}", e.getCause().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }


        log.info("Fetched {} prices for '{}' in {}ms", results.size(), product, System.currentTimeMillis() - start);
        return results;
    }

    @Override
    public PriceResult findCheapest(String product) {
        List<PriceResult> all = fetchAllPrices(product);
        return all.stream()
                .min(Comparator.comparingDouble(PriceResult::getPrice))
                .orElseThrow(() -> new IllegalStateException("No prices found for " + product));
    }


    @Override
    public Map<String, Object> getPoolStats() {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) threadPool;
        return Map.of(
            "activeThreads",   pool.getActiveCount(),
            "poolSize",        pool.getPoolSize(),
            "queuedTasks",     pool.getQueue().size(),
            "completedTasks",  pool.getCompletedTaskCount()
        );
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down thread pool...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Forcing shutdown of thread pool...");
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

package dev.hieunv.reactive.dao;

import dev.hieunv.reactive.dto.Customer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

@Component
public class CustomerDao {

    private static void sleepExecution() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Customer> getCustomers() {
        return IntStream.rangeClosed(1, 50)
                .peek(i -> sleepExecution())
                .peek(i -> System.out.println("Fetching customer " + i))
                .mapToObj(i -> new Customer(i, "Customer " + i))
                .toList();
    }

    public Flux<Customer> loadAllCustomers() {
        return Flux.range(1, 50)
                .delayElements(Duration.ofSeconds(1))
                .doOnNext(i -> System.out.println("Fetching customer in stream flow " + i))
                .map(i -> new Customer(i, "Customer " + i));
    }

    public Flux<Customer> loadAllCustomersForHandler() {
        return Flux.range(1, 50)
                .doOnNext(i -> System.out.println("Fetching customer in stream flow " + i))
                .map(i -> new Customer(i, "Customer " + i));
    }
}

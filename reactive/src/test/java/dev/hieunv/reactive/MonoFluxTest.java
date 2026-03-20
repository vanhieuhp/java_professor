package dev.hieunv.reactive;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MonoFluxTest {

    @Test
    public void testMono() {
        Mono<?> monoString = Mono.just("Hello World!")
                .then(Mono.error(new RuntimeException("Exception occur"))).log();
        monoString.subscribe(System.out::println, (e) -> System.out.println("Error: " + e.getMessage()));
    }

    @Test
    public void testFlux() {
        Flux<String> fluxString = Flux.just("Spring", "Spring Boot", "ReactiveSpring")
                .concatWithValues("AWS")
                .concatWith(Flux.error(new RuntimeException("Exception occur")))
                .concatWithValues("Azure")
                .log();
        fluxString.subscribe(System.out::println, (e) -> System.out.println("Error: " + e.getMessage()));

    }
}

package com.smartqueue.config;

import com.smartqueue.model.CheckoutCounter;
import com.smartqueue.model.CounterStatus;
import com.smartqueue.repository.CheckoutCounterRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner loadInitialCounters(
            CheckoutCounterRepository counterRepository) {

        return args -> {
            createCounterIfMissing(counterRepository, "Counter 1");
            createCounterIfMissing(counterRepository, "Counter 2");
            createCounterIfMissing(counterRepository, "Counter 3");
        };
    }

    private void createCounterIfMissing(
            CheckoutCounterRepository counterRepository,
            String counterName) {

        if (!counterRepository.existsByName(counterName)) {
            CheckoutCounter counter =
                    new CheckoutCounter(counterName, CounterStatus.OPEN);

            counterRepository.save(counter);
        }
    }
}

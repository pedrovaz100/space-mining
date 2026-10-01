package br.com.fiap.validatorservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ValidationRetryService {

    // Simula ~50% de chance de falha de comunicação e tenta novamente com exponential backoff
    @Retryable(
            includes = ValidationFailedException.class,
            maxRetries = 5,
            delay = 500,
            multiplier = 2,
            maxDelay = 10_000,
            jitter = 100
    )
    public void validateWithRetry(String command) {
        if (Math.random() < 0.5) {
            log.warn("🔴 Falha simulada na validação do comando: {}", command);
            throw new ValidationFailedException(command);
        }
        log.info("🟢 Comando {} passou na validação simulada", command);
    }

}

package br.com.fiap.validatorservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ValidatorController {

    private final RabbitTemplate rabbitTemplate;
    private final ValidationRetryService validationRetryService;

    // Comandos válidos aceitos pelo sistema
    private static final List<String> COMANDOS_VALIDOS = List.of(
            "RIGHT", "LEFT", "FRONT", "BACK", "OPEN", "CLOSE"
    );

    public record CommandRequest(String command) {}
    public record ValidatorResponse(String status) {}

    @PostMapping("/validate")
    public ValidatorResponse validate(@RequestBody CommandRequest request) {
        String comando = request.command();

        // Verifica se o comando é válido (List.of(...).contains(null) lança NPE, por isso o check explícito)
        if (comando == null || !COMANDOS_VALIDOS.contains(comando)) {
            log.warn("🔴 Comando inválido recebido: {}", comando);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Comando inválido: " + comando + ". Válidos: " + COMANDOS_VALIDOS);
        }

        // Simula falha de validação com retry + exponential backoff
        try {
            validationRetryService.validateWithRetry(comando);
        } catch (ValidationFailedException e) {
            log.error("🔴 Validação do comando {} falhou após todas as tentativas de retry", comando);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Falha na validação após múltiplas tentativas: " + comando);
        }

        // Publica o comando na fila do RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_NAME,
                RabbitConfig.ROUTING_KEY,
                comando
        );

        log.info("🟢 Comando {} validado e enviado para a fila", comando);
        return new ValidatorResponse("Comando " + comando + " enviado para processamento");
    }

}

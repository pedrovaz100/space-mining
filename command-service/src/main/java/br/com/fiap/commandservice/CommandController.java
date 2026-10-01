package br.com.fiap.commandservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CommandController {

    private final RestTemplate restTemplate;

    public record CommandRequest(String command) {}
    public record ValidatorResponse(String status) {}

    @PostMapping("/command")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ValidatorResponse sendCommand(@RequestBody CommandRequest request) {
        log.info("Enviando comando para validação: {}", request.command());

        try {
            ValidatorResponse response = restTemplate.postForObject(
                    "http://VALIDATOR-SERVICE/validate",
                    request,
                    ValidatorResponse.class
            );
            log.info("🟢 Resposta do validator: {}", response.status());
            return response;
        } catch (HttpStatusCodeException e) {
            // Repassa erros de validação (ex: comando inválido) com o status original
            log.warn("🔴 Validator rejeitou o comando: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            // Falha de comunicação (Validator indisponível, DNS/Eureka, timeout, etc.)
            log.error("🔴 Falha de comunicação com o Validator Service: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Validator Service indisponível, tente novamente mais tarde");
        }
    }

}

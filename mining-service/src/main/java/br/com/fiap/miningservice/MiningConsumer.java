package br.com.fiap.miningservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Consome os comandos da fila e registra no banco
@Slf4j
@Component
@RequiredArgsConstructor
public class MiningConsumer {

    public static final String QUEUE_NAME = "mining-queue";

    private final CommandCountRepository commandCountRepository;

    @RabbitListener(queues = QUEUE_NAME)
    public void executeCommand(String command) {
        // Loga a execução do comando no robô
        log.info("🤖 Robô executando comando: {}", command);

        // Busca o registro do comando no banco, ou cria um novo com 0 execuções
        var registro = commandCountRepository.findById(command).orElseGet(
                () -> new CommandCount(command, 0)
        );

        // Incrementa a contagem e salva
        registro.setTotalExecutions(registro.getTotalExecutions() + 1);
        commandCountRepository.save(registro);

        log.info("✅ Comando {} registrado. Total de execuções: {}", command, registro.getTotalExecutions());
    }

}

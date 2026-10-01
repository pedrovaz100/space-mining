package br.com.fiap.validatorservice;

// Lançada quando a validação simulada falha; usada pelo mecanismo de retry
public class ValidationFailedException extends RuntimeException {

    public ValidationFailedException(String command) {
        super("Falha simulada na validação do comando: " + command);
    }

}

package br.com.fiap.miningservice;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Cada linha guarda quantas vezes um comando foi executado
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommandCount {

    @Id
    private String command; // ex: "LEFT", "FRONT"

    private Integer totalExecutions;

}

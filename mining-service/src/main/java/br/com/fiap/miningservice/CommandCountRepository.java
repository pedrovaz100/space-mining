package br.com.fiap.miningservice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandCountRepository extends JpaRepository<CommandCount, String> {
}

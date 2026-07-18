package com.creditai.repository;

import com.creditai.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByDni(String dni);
    boolean existsByDni(String dni);
    List<Client> findByStatus(Client.ClientStatus status);

    @Query("SELECT COUNT(c) FROM Client c WHERE c.status = 'ACTIVE'")
    long countActive();
}

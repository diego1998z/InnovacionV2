package com.creditai.service;

import com.creditai.dto.*;
import com.creditai.entity.Client;
import com.creditai.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    public List<ClientResponse> findAll() {
        return clientRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    public ClientResponse findById(Long id) {
        Client c = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        return mapToResponse(c);
    }

    public ClientResponse create(ClientRequest req) {
        if (clientRepository.existsByDni(req.dni()))
            throw new RuntimeException("Ya existe un cliente con DNI: " + req.dni());
        Client c = Client.builder()
                .dni(req.dni())
                .fullName(req.fullName())
                .age(req.age())
                .address(req.address())
                .phone(req.phone())
                .email(req.email())
                .monthlyIncome(req.monthlyIncome())
                .totalSavings(req.totalSavings())
                .currentDebts(req.currentDebts())
                .status(Client.ClientStatus.ACTIVE)
                .build();
        return mapToResponse(clientRepository.save(c));
    }

    public ClientResponse update(Long id, ClientRequest req) {
        Client c = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        c.setFullName(req.fullName());
        c.setAge(req.age());
        c.setAddress(req.address());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setMonthlyIncome(req.monthlyIncome());
        c.setTotalSavings(req.totalSavings());
        c.setCurrentDebts(req.currentDebts());
        return mapToResponse(clientRepository.save(c));
    }

    public void delete(Long id) {
        Client c = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        c.setStatus(Client.ClientStatus.INACTIVE);
        clientRepository.save(c);
    }

    private ClientResponse mapToResponse(Client c) {
        return new ClientResponse(c.getId(), c.getDni(), c.getFullName(), c.getAge(),
                c.getAddress(), c.getPhone(), c.getEmail(),
                c.getMonthlyIncome(), c.getTotalSavings(), c.getCurrentDebts(),
                c.getStatus().name(), c.getCreatedAt());
    }
}

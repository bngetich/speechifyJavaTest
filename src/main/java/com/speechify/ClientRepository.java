package com.speechify;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ClientRepository {
    private final String dbFile;
    private final ObjectMapper objectMapper;
    private static final java.util.logging.Logger logger = java.util.logging.Logger
            .getLogger(ClientRepository.class.getName());

    public ClientRepository() {
        this("db.json");
    }

    public ClientRepository(String dbFile) {
        this.dbFile = dbFile;
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<Client> getById(String id) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ArrayNode clients = loadClientArray();
                if (clients == null) {
                    return null;
                }
                return findClientById(clients, id);
            } catch (IOException e) {
                logger.severe("Error loading client by id " + id + ": " + e.getMessage());
                return null;
            }
        });
    }

    public CompletableFuture<List<Client>> getAll() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ArrayNode clients = loadClientArray();
                if (clients == null) {
                    return new ArrayList<>();
                }
                return findAllClients(clients);
            } catch (IOException e) {
                logger.severe("Error loading all clients: " + e.getMessage());
                return new ArrayList<>();
            }
        });
    }

    private Client findClientById(ArrayNode clients, String id) {
        for (int i = 0; i < clients.size(); i++) {
            ObjectNode clientNode = (ObjectNode) clients.get(i);
            if (clientNode.get("id").asText().equals(id)) {
                return mapNodeToClient(clientNode);
            }
        }

        return null;
    }

    private List<Client> findAllClients(ArrayNode clients) {
        List<Client> clientList = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) {
            ObjectNode clientNode = (ObjectNode) clients.get(i);
            clientList.add(mapNodeToClient(clientNode));
        }

        return clientList;
    }

    private Client mapNodeToClient(ObjectNode clientNode) {
        Client client = new Client();
        client.setId(clientNode.get("id").asText());
        client.setName(clientNode.get("name").asText());
        return client;
    }

    private ArrayNode loadClientArray() throws IOException {
        File file = new File(dbFile);
        if (!file.exists()) {
            return null;
        }

        ObjectNode root = (ObjectNode) objectMapper.readTree(file);
        if (root == null || !root.has("clients")) {
            logger.warning("Invalid JSON structure: missing 'clients' array");
            return null;
        }
        return (ArrayNode) root.get("clients");
    }
}
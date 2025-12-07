package com.speechify;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class UserService {
    private static final String DB_FILE = "db.json";
    private final ObjectMapper objectMapper;
    private ClientRepository clientRepository;

    public UserService() {
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<Boolean> addUser(
            String firstname,
            String surname,
            String email,
            LocalDate dateOfBirth,
            String clientId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (firstname == null || surname == null || email == null) {
                    return false;
                }

                File dbFile = new File(DB_FILE);
                if (!dbFile.exists()) {
                    return false;
                }

                ObjectNode root = (ObjectNode) objectMapper.readTree(dbFile);
                ArrayNode users = (ArrayNode) root.get("users");

                // Check if user with email already exists
                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("email").asText().equals(email)) {
                        return false;
                    }
                }

                // Check age
                int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
                if (age < 21) {
                    return false;
                }

                // Get client
                clientRepository = new ClientRepository();
                Client client = clientRepository.getById(clientId).join();
                if (client == null) {
                    System.err.println("Client not found");
                    return false;
                }

                // Create user
                User user = new User();
                user.setId(UUID.randomUUID().toString());
                user.setClient(client);
                user.setDateOfBirth(dateOfBirth);
                user.setEmail(email);
                user.setFirstname(firstname);
                user.setSurname(surname);

                // Set credit limit based on client
                if ("VeryImportantClient".equals(client.getName())) {
                    user.setHasCreditLimit(false);
                } else if ("ImportantClient".equals(client.getName())) {
                    user.setHasCreditLimit(true);
                    user.setCreditLimit(10000 * 2);
                } else {
                    user.setHasCreditLimit(true);
                    user.setCreditLimit(10000);
                }

                // Add user to database
                users.add(objectMapper.valueToTree(user));
                objectMapper.writeValue(dbFile, root);
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> updateUser(User user) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (user == null) {
                    return false;
                }

                File dbFile = new File(DB_FILE);
                if (!dbFile.exists()) {
                    return false;
                }

                ObjectNode root = (ObjectNode) objectMapper.readTree(dbFile);
                ArrayNode users = (ArrayNode) root.get("users");

                // Find and update user
                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("id").asText().equals(user.getId())) {
                        users.set(i, objectMapper.valueToTree(user));
                        objectMapper.writeValue(dbFile, root);
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                return false;
            }
        });
    }

    public CompletableFuture<List<User>> getAllUsers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File dbFile = new File(DB_FILE);
                if (!dbFile.exists()) {
                    return new ArrayList<>();
                }

                ObjectNode root = (ObjectNode) objectMapper.readTree(dbFile);
                ArrayNode users = (ArrayNode) root.get("users");
                List<User> userList = new ArrayList<>();

                for (int i = 0; i < users.size(); i++) {
                    User user = objectMapper.treeToValue(users.get(i), User.class);
                    userList.add(user);
                }
                return userList;
            } catch (IOException e) {
                return new ArrayList<>();
            }
        });
    }

    public CompletableFuture<User> getUserByEmail(String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File dbFile = new File(DB_FILE);
                if (!dbFile.exists()) {
                    return null;
                }

                ObjectNode root = (ObjectNode) objectMapper.readTree(dbFile);
                ArrayNode users = (ArrayNode) root.get("users");

                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("email").asText().equals(email)) {
                        return objectMapper.treeToValue(userNode, User.class);
                    }
                }
                return null;
            } catch (IOException e) {
                return null;
            }
        });
    }
} 
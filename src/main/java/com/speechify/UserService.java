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
    private static final java.util.logging.Logger logger = 
        java.util.logging.Logger.getLogger(UserService.class.getName());

    private final ObjectMapper objectMapper;
    private ClientRepository clientRepository;
    private LRUCache<User> lruCache;

    public UserService(){
        this(new ClientRepository());
    }

    public UserService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
        this.objectMapper = new ObjectMapper();
        this.lruCache = LRUCacheProvider.createLRUCache(new CacheLimits(5));
    }

    public CompletableFuture<Boolean> addUser(
            String firstname,
            String surname,
            String email,
            LocalDate dateOfBirth,
            String clientId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isValidUserInput(firstname, surname, email)) {
                    return false;
                }

                if (!isValidAge(dateOfBirth)) {
                    return false;
                }

                // Get client
                Client client = clientRepository.getById(clientId).join();
                if (client == null) {
                    logger.warning("Client not found with id: " + clientId);
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
                ClientType.fromName(client.getName()).applyCreditLimit(user);

                // Add user to database
                ArrayNode users = loadUsersArray();
                if (users == null) {
                    return false;
                }
                users.add(objectMapper.valueToTree(user));

                File dbFile = new File(DB_FILE);
                ObjectNode root = objectMapper.createObjectNode();
                root.set("users", users);
                objectMapper.writeValue(dbFile, root);

                // Add user to cache
                lruCache.set(user.getEmail(), user);
                return true;
            } catch (IOException e) {
                logger.severe("Error adding user: " + e.getMessage());
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
                ArrayNode users = loadUsersArray();
                if (users == null) {
                    return false;
                }

                // Find and update user
                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("id").asText().equals(user.getId())) {
                        users.set(i, objectMapper.valueToTree(user));

                        ObjectNode root = objectMapper.createObjectNode();
                        root.set("users", users);
                        objectMapper.writeValue(dbFile, root);

                        // Update cache to keep it synchronized
                        lruCache.set(user.getEmail(), user);
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                logger.severe("Error updating user: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<List<User>> getAllUsers() {
        return CompletableFuture.supplyAsync(() -> {
            try {

                ArrayNode users = loadUsersArray();
                if (users == null) {
                    return new ArrayList<>();
                }

                List<User> userList = new ArrayList<>();
                for (int i = 0; i < users.size(); i++) {
                    User user = objectMapper.treeToValue(users.get(i), User.class);
                    userList.add(user);
                }
                return userList;
            } catch (IOException e) {
                logger.severe("Error getting all users: " + e.getMessage());
                return new ArrayList<>();
            }
        });
    }

    public CompletableFuture<User> getUserByEmail(String email) {
        // check if exists in cache
        User user = lruCache.get(email);
        if (user != null)
            return CompletableFuture.completedFuture(user);
        
        return CompletableFuture.supplyAsync(() -> {
            try {

                ArrayNode users = loadUsersArray();
                if (users == null) {
                    return null;
                }

                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("email").asText().equals(email)) {
                        User foundUser = objectMapper.treeToValue(userNode, User.class);
                        // Cache the user we just fetched
                        if (foundUser != null) {
                            lruCache.set(email, foundUser);
                        }
                        return foundUser;
                    }
                }
                return null;
            } catch (IOException e) {
                logger.severe("Error getting user by email " + email + ": " + e.getMessage());
                return null;
            }
        });
    }

    private ArrayNode loadUsersArray() throws IOException {
        File file = new File(DB_FILE);
        if (!file.exists()) {
            return null;
        }

        ObjectNode root = (ObjectNode) objectMapper.readTree(file);
        if (root == null || !root.has("users")) {
            return null;
        }
        return (ArrayNode) root.get("users");
    }

    private boolean isValidUserInput(String firstname, String surname, String email) {
        return firstname != null && surname != null && email != null;
    }

    private boolean isValidAge(LocalDate dateOfBirth) {
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        return age >= 21;
    }

    private CompletableFuture<Boolean> userExistsByEmail(String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ArrayNode users = loadUsersArray();
                if (users == null) {
                    return false;
                }

                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("email").asText().equals(email)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                logger.severe("Error checking user existence: " + e.getMessage());
                return false;
            }
        });
    }
}
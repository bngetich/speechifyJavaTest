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
    private static final String DEFAULT_DB_FILE = "src/main/java/com/speechify/db.json";
    private static final java.util.logging.Logger logger = 
        java.util.logging.Logger.getLogger(UserService.class.getName());

    private final String dbFile;
    private final ObjectMapper objectMapper;
    private final ClientRepository clientRepository;
    private final LRUCache<User> lruCache;

    public UserService() {
        this(new ClientRepository(DEFAULT_DB_FILE), DEFAULT_DB_FILE);
    }

    public UserService(ClientRepository clientRepository, String dbFile) {
        this.clientRepository = clientRepository;
        this.dbFile = dbFile;
        this.objectMapper = new ObjectMapper();
        this.lruCache = LRUCacheProvider.createLRUCache(new CacheLimits(500));
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

                ObjectNode root = loadRootObject();
                if (root == null) {
                    return false;
                }
                ArrayNode users = getOrCreateUsersArray(root);
                if (emailAlreadyExists(users, email)) {
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
                users.add(objectMapper.valueToTree(user));
                persistRootObject(root);

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

                ObjectNode root = loadRootObject();
                if (root == null) {
                    return false;
                }
                ArrayNode users = getOrCreateUsersArray(root);

                // Find and update user
                for (int i = 0; i < users.size(); i++) {
                    ObjectNode userNode = (ObjectNode) users.get(i);
                    if (userNode.get("id").asText().equals(user.getId())) {
                        users.set(i, objectMapper.valueToTree(user));
                        persistRootObject(root);

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
                ObjectNode root = loadRootObject();
                if (root == null) {
                    return new ArrayList<>();
                }
                ArrayNode users = getOrCreateUsersArray(root);

                List<User> userList = new ArrayList<>();
                for (int i = 0; i < users.size(); i++) {
                    User user = objectMapper.treeToValue(users.get(i), User.class);
                    lruCache.set(user.getEmail(), user);
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
                ObjectNode root = loadRootObject();
                if (root == null) {
                    return null;
                }
                ArrayNode users = getOrCreateUsersArray(root);

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

    private ObjectNode loadRootObject() throws IOException {
        File file = new File(dbFile);
        if (!file.exists()) {
            return null;
        }

        ObjectNode root = (ObjectNode) objectMapper.readTree(file);
        if (root == null) {
            return null;
        }
        return root;
    }

    private ArrayNode getOrCreateUsersArray(ObjectNode root) {
        if (!root.has("users")) {
            ArrayNode users = objectMapper.createArrayNode();
            root.set("users", users);
            return users;
        }
        return (ArrayNode) root.get("users");
    }

    private void persistRootObject(ObjectNode root) throws IOException {
        File file = new File(dbFile);
        objectMapper.writeValue(file, root);
    }

    private boolean emailAlreadyExists(ArrayNode users, String email) {
        for (int i = 0; i < users.size(); i++) {
            ObjectNode userNode = (ObjectNode) users.get(i);
            if (userNode.has("email") && email.equals(userNode.get("email").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidUserInput(String firstname, String surname, String email) {
        if (firstname == null || surname == null || email == null || firstname.isBlank() || surname.isBlank()) {
            return false;
        }
        return email.contains("@");
    }

    private boolean isValidAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return false;
        }
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        return age >= 21;
    }
}

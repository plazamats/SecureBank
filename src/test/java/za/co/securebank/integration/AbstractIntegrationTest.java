package za.co.securebank.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import za.co.securebank.entity.User;
import za.co.securebank.enums.RoleName;
import za.co.securebank.repository.AuditLogRepository;
import za.co.securebank.repository.BankAccountRepository;
import za.co.securebank.repository.RefreshTokenRepository;
import za.co.securebank.repository.RoleRepository;
import za.co.securebank.repository.TransactionRepository;
import za.co.securebank.repository.UserRepository;
import za.co.securebank.security.JwtService;

/**
 * Base class for end to end tests. They run the full application (security filter chain, controllers, services,
 * JPA) against the database configured by the "test" profile and clean all data before every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RoleRepository roleRepository;
    @Autowired
    protected BankAccountRepository accountRepository;
    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;

    protected record Tokens(String accessToken, String refreshToken) {
    }

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        auditLogRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    protected JsonNode send(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    protected JsonNode postJson(String url, Object body, String token, int expectedStatus) throws Exception {
        return send(post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
                token, expectedStatus);
    }

    protected JsonNode putJson(String url, Object body, String token, int expectedStatus) throws Exception {
        return send(put(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)),
                token, expectedStatus);
    }

    protected JsonNode getJson(String url, String token, int expectedStatus) throws Exception {
        return send(get(url), token, expectedStatus);
    }

    protected JsonNode patchJson(String url, String token, int expectedStatus) throws Exception {
        return send(patch(url), token, expectedStatus);
    }

    protected void register(String email) throws Exception {
        postJson("/api/v1/auth/register", Map.of("email", email, "password", PASSWORD,
                "firstName", "Test", "lastName", "User"), null, 201);
    }

    protected Tokens login(String email, String password) throws Exception {
        JsonNode body = postJson("/api/v1/auth/login", Map.of("email", email, "password", password), null, 200);
        return new Tokens(body.get("accessToken").asText(), body.get("refreshToken").asText());
    }

    protected Tokens registerAndLogin(String email) throws Exception {
        register(email);
        return login(email, PASSWORD);
    }

    /** Creates an administrator directly in the database (there is deliberately no public way to become admin). */
    protected Tokens createAdminAndLogin(String email) throws Exception {
        Set<za.co.securebank.entity.Role> roles = new HashSet<>();
        roles.add(roleRepository.findByName(RoleName.ROLE_USER).orElseThrow());
        roles.add(roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow());
        userRepository.save(User.builder().email(email).passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Admin").lastName("User").emailVerified(true).roles(roles).build());
        return login(email, PASSWORD);
    }

    protected JsonNode createAccount(String token, String type) throws Exception {
        return postJson("/api/v1/accounts", Map.of("type", type, "name", "Main"), token, 201);
    }

    protected JsonNode deposit(String token, long accountId, String amount) throws Exception {
        return postJson("/api/v1/transactions/deposit",
                Map.of("accountId", accountId, "amount", new BigDecimal(amount)), token, 201);
    }

    protected BigDecimal balanceOf(String token, long accountId) throws Exception {
        JsonNode node = getJson("/api/v1/accounts/" + accountId + "/balance", token, 200);
        return new BigDecimal(node.get("balance").asText());
    }

    protected Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }
}

package com.miguelpazatto.orderapi.integration.core;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceExceptionHandler;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.products.entities.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorContractIntegrationTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ResourceExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"integrity,409", "optimistic-lock,409", "authentication,401", "access,403",
            "business,400", "missing,404", "unexpected,500"})
    void shouldSerializeErrorStatusWithoutLeakingInternalDetails(String error, int status) throws Exception {
        mvc.perform(get("/test-errors/{error}", error))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("status").value(status))
                .andExpect(jsonPath("detail").value(not(containsString("private-internal-value"))))
                .andExpect(jsonPath("timestamp").exists());
    }

    @RestController
    static class FailingController {
        @GetMapping("/test-errors/{error}")
        public void fail(@PathVariable String error) {
            switch (error) {
                case "integrity" -> throw new DataIntegrityViolationException("private-internal-value");
                case "optimistic-lock" -> throw new ObjectOptimisticLockingFailureException(Product.class, UUID.randomUUID());
                case "authentication" -> throw new AuthenticationCredentialsNotFoundException("Login necessário");
                case "access" -> throw new AccessDeniedException("Acesso negado");
                case "business" -> throw new BusinessRuleException("Operação inválida");
                case "missing" -> throw new ResourceNotFoundException("Recurso inexistente");
                default -> throw new IllegalStateException("private-internal-value");
            }
        }
    }
}

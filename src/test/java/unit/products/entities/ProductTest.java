package unit.products.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.Assert.isInstanceOf;

public class ProductTest {

    @Test
    @DisplayName("Deve inicializar com status ACTIVE quando o estoque for maior que zero")
    void shouldInitializeAsActiveWhenStockIsPositive() {
        // Given
        Integer stock = 10;

        // When
        Product product = new Product("Teclado", "Teclado Mecânico", new BigDecimal("300.00"), stock, "SKU-123");

        // Then
        assertEquals(ProductStatus.ACTIVE, product.getProductStatus(), "Produto com estoque positivo deve nascer como ACTIVE.");
    }

    @Test
    @DisplayName("Deve inicializar com status OUT_OF_STOCK quando o estoque for exatamente zero")
    void shouldInitializeAsOutOfStockWhenStockIsZero() {
        // Given
        Integer stock = 0;

        // When
        Product product = new Product("Teclado", "Teclado Mecânico", new BigDecimal("300.00"), stock, "SKU-123");

        // Then
        assertEquals(ProductStatus.OUT_OF_STOCK, product.getProductStatus(), "Produto com estoque zerado deve nascer como OUT_OF_STOCK.");
    }

    @Test
    @DisplayName("Deve inicializar com status OUT_OF_STOCK quando o estoque for nulo (Null Safety)")
    void shouldInitializeAsOutOfStockWhenStockIsNull() {
        // Given
        Integer stock = null;

        // When
        Product product = new Product("Teclado", "Teclado Mecânico", new BigDecimal("300.00"), stock, "SKU-123");

        // Then
        assertEquals(ProductStatus.OUT_OF_STOCK, product.getProductStatus(), "Produto com estoque nulo deve ser tratado e nascer como OUT_OF_STOCK.");
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar criar produto com estoque negativo")
    void shouldThrowExceptionWhenStockIsNegative() {
        // Given
        Integer negativeStock = -5;

        // When - Then
        assertThatThrownBy(() ->
                new Product("Teclado", "Teclado Mecânico", new BigDecimal("300.00"), negativeStock, "SKU-123")
        )
        .isInstanceOf(BusinessRuleException.class)
        .hasMessage("O estoque inicial não pode ser negativo.");
    }
}

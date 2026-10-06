package com.miguelpazatto.orderapi.unit.products.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProductTest {

    @Test
    @DisplayName("Deve inicializar com status ACTIVE quando o estoque for maior que zero")
    void shouldInitializeAsActiveWhenStockIsPositive() {
        // Given
        Integer stock = 10;

        // When
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                stock,
                "SKU-123");

        // Then
        assertEquals(ProductStatus.ACTIVE, product.getProductStatus(), "Produto com estoque positivo deve nascer como ACTIVE.");
    }

    @Test
    @DisplayName("Deve inicializar com status OUT_OF_STOCK quando o estoque for exatamente zero")
    void shouldInitializeAsOutOfStockWhenStockIsZero() {
        // Given
        Integer stock = 0;

        // When
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                stock,
                "SKU-123");

        // Then
        assertEquals(ProductStatus.OUT_OF_STOCK, product.getProductStatus(), "Produto com estoque zerado deve nascer como OUT_OF_STOCK.");
    }

    @Test
    @DisplayName("Deve inicializar com status OUT_OF_STOCK quando o estoque for nulo (Null Safety)")
    void shouldInitializeAsOutOfStockWhenStockIsNull() {
        // Given
        Integer stock = null;

        // When
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                stock,
                "SKU-123");

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
                new Product("Teclado", "Teclado Mecânico", new BigDecimal("300.00"), negativeStock, "SKU-123"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O estoque inicial não pode ser negativo.");
    }

    @Test
    @DisplayName("Deve alterar o estoque e o status para ACTIVE quando a nova quantidade for positiva")
    void shouldUpdateStockWhenNewStockIsPositive() {
        // Given
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                5,
                "SKU-123");
        Integer newStock = 10;

        // When
        product.updateStock(newStock);

        // Then
        assertThat(product.getAvailableStock()).isEqualTo(newStock);
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar alterar estoque com valor negativo")
    void shouldThrowExceptionWhenNewStockIsNegative() {
        // Given
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                5,
                "SKU-123");
        Integer newStock = -10;

        // When - Then
        assertThatThrownBy(() -> product.updateStock(newStock))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O estoque não pode ser nulo ou negativo.");
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar alterar estoque com valor nulo")
    void shouldThrowExceptionWhenNewStockIsNull() {
        // Given
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                5,
                "SKU-123");
        Integer newStock = null;

        // When - Then
        assertThatThrownBy(() -> product.updateStock(newStock))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O estoque não pode ser nulo ou negativo.");
    }

     @Test
    @DisplayName("Deve reduzir o estoque e manter o status atual quando a quantidade retirada for menor que o estoque disponível")
    void shouldDecreaseStockWhenQuantityIsLessThanAvailable() {
        // Given
        Integer quantity = 5;
        Integer initialStock = 10;
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                initialStock,
                "SKU-123");


        // When
        product.decreaseStock(quantity);

        // Then
        assertThat(product.getAvailableStock()).isEqualTo(initialStock - quantity);
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve reduzir o estoque e alterar o status para OUT_OF_STOCK quando a quantidade retirada zerar o estoque")
    void shouldDecreaseStockAndChangeStatusToOutOfStockWhenQuantityEqualsAvailable() {
        // Given
        Integer quantity = 10;
        Integer initialStock = 10;
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                initialStock,
                "SKU-123");

        // When
        product.decreaseStock(quantity);

        // Then
        assertThat(product.getAvailableStock()).isZero();
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("Deve lançar exceção quando a quantidade for zero ou negativa")
    void shouldThrowExceptionWhenQuantityIsZeroOrNegative(int invalidQuantity) {
        // Given
        Integer initialStock = 5;
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                initialStock,
                "SKU-123");

        // When - Then
        assertThatThrownBy(() -> product.decreaseStock(invalidQuantity))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A quantidade para baixar do estoque deve ser maior que zero.");
    }


    @Test
    @DisplayName("Deve lançar exceção quando a quantidade a ser subtraída for maior que o estoque")
    void shouldThrowExceptionWhenQuantityIsBiggerThanAvailableStock() {
        // Given
        Integer quantity = 10;
        Integer initialStock = 5;
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                initialStock,
                "SKU-123");

        // When - Then
        assertThatThrownBy(() -> product.decreaseStock(quantity))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Estoque insuficiente para o produto: " + product.getName());
    }

    @Test
    @DisplayName("Deve alterar o preço quando ele for positivo")
    void shouldUpdatePriceWhenNewPriceIsPositive() {
        // Given
        BigDecimal newPrice = BigDecimal.valueOf(100);
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                10,
                "SKU-123");

        // When
        product.updatePrice(newPrice);

        // Then
        assertThat(product.getPrice()).isEqualByComparingTo(newPrice);
    }

    @Test
    @DisplayName("Deve lançar exceção quando preço for nulo")
    void shouldThrowExceptionWhenNewPriceIsNull() {
        // Given
        BigDecimal newPrice = null;
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                10,
                "SKU-123");

        // When - Then
        assertThatThrownBy(() -> product.updatePrice(newPrice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O preço deve ser maior que zero.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01", "-150.50"})
    @DisplayName("Deve lançar exceção quando o novo preço for zero ou negativo")
    void shouldThrowExceptionWhenNewPriceIsZeroOrNegative(String invalidPriceString) {
        // Given
        BigDecimal invalidPrice = new BigDecimal(invalidPriceString);
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                10,
                "SKU-123");

        // When - Then
        assertThatThrownBy(() -> product.updatePrice(invalidPrice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("O preço deve ser maior que zero.");
    }

    @Test
    @DisplayName("Deve atualizar nome e descrição quando ambos os valores forem válidos")
    void shouldUpdateBothNameAndDescription() {
        // Given
        Product product = new Product(
                "Teclado",
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                10,
                "SKU-123");
        String newName = "Teclado Gamer";
        String newDescription = "Teclado Mecânico RGB";

        // When
        product.updateDetails(newName, newDescription);

        // Then
        assertThat(product.getName()).isEqualTo(newName);
        assertThat(product.getDescription()).isEqualTo(newDescription);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Deve atualizar apenas a descrição e manter o nome original quando o novo nome for inválido")
    void shouldUpdateOnlyDescriptionWhenNewNameIsInvalid(String invalidName) {
        // Given
        String originalName = "Teclado";
        Product product = new Product(
                originalName,
                "Teclado Mecânico",
                new BigDecimal("300.00"),
                10,
                "SKU-123");
        String newDescription = "Teclado com Switch Red";

        // When
        product.updateDetails(invalidName, newDescription);

        // Then
        assertThat(product.getName()).isEqualTo(originalName);
        assertThat(product.getDescription()).isEqualTo(newDescription);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Deve atualizar apenas o nome e manter a descrição original quando a nova descrição for inválida")
    void shouldUpdateOnlyNameWhenNewDescriptionIsInvalid(String invalidDescription) {
        // Given
        String originalDescription = "Teclado Mecânico";
        Product product = new Product("Teclado", originalDescription, new BigDecimal("300.00"), 10, "SKU-123");
        String newName = "Teclado Gamer";

        // When
        product.updateDetails(newName, invalidDescription);

        // Then
        assertThat(product.getName()).isEqualTo(newName);
        assertThat(product.getDescription()).isEqualTo(originalDescription);
    }

    @Test
    @DisplayName("Deve inativar o produto alterando o status para INACTIVE")
    void shouldDeactivateProduct() {
        // Given
        Product product = new Product(
                "Mouse",
                "Mouse",
                new BigDecimal("100"),
                10,
                "SKU-1");

        // When
        product.deactivate();

        // Then
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar inativar um produto que já está inativo")
    void shouldThrowExceptionWhenDeactivatingAlreadyInactiveProduct() {
        // Given
        Product product = new Product("Mouse", "Mouse", new BigDecimal("100"), 10, "SKU-1");
        product.deactivate();

        // When - Then
        assertThatThrownBy(() -> product.deactivate())
                .isInstanceOf(DataConflictException.class)
                .hasMessage("O produto já se encontra com o status INACTIVE");
    }

    @Test
    @DisplayName("Deve ativar o produto como ACTIVE quando houver estoque disponível")
    void shouldActivateAsActiveWhenStockIsPositive() {
        // Given
        Product product = new Product(
                "Mouse",
                "Mouse",
                new BigDecimal("100"),
                10,
                "SKU-1");
        product.deactivate();

        // When
        product.activate();

        // Then
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("Deve ativar o produto como OUT_OF_STOCK quando o estoque for zero")
    void shouldActivateAsOutOfStockWhenStockIsZero() {
        // Given
        Product product = new Product(
                "Mouse",
                "Mouse",
                new BigDecimal("100"),
                0,
                "SKU-1");
        product.deactivate();

        // When
        product.activate();

        // Then
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar ativar um produto que já está ACTIVE")
    void shouldThrowExceptionWhenActivatingAlreadyActiveProduct() {
        // Given
        Product product = new Product("Mouse", "Mouse", new BigDecimal("100"), 10, "SKU-1");

        // When - Then
        assertThatThrownBy(() -> product.activate())
                .isInstanceOf(DataConflictException.class)
                .hasMessage("O produto já se encontra com o status ACTIVE");
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar ativar um produto que já está OUT_OF_STOCK")
    void shouldThrowExceptionWhenActivatingAlreadyOutOfStockProduct() {
        // Given
        Product product = new Product("Mouse", "Mouse", new BigDecimal("100"), 0, "SKU-1");

        // When - Then
        assertThatThrownBy(() -> product.activate())
                .isInstanceOf(DataConflictException.class)
                .hasMessage("O produto já se encontra com o status OUT_OF_STOCK");
    }

}

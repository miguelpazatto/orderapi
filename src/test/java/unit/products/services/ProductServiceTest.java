package unit.products.services;

import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
import com.miguelpazatto.orderapi.products.services.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Test
    @DisplayName("Deve salvar o produto delegando a chamada para o repository")
    void shouldSaveProductSucessfully() {
        // Given
        Product productToSave = new Product(
                "Monitor",
                "Monitor 24p",
                new BigDecimal("800.00"),
                15,
                "SKU-999");

        Mockito.when(productRepository.save(productToSave)).thenReturn(productToSave);

        // When
        Product result = productService.save(productToSave);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(productToSave.getName());

        Mockito.verify(productRepository, Mockito.times(1)).save(productToSave);
        Mockito.verifyNoMoreInteractions(productRepository);
    }




}

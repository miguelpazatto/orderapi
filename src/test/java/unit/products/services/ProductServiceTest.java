package unit.products.services;

import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.products.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.products.dtos.ProductResponseDTO;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("Deve retornar uma lista de ProductResponseDTO quando houver produtos no banco")
    void shouldReturnListOfProductResponseDTOWhenProductsExist() {
        // Given
        Product product1 = new Product(
                "Monitor",
                "Monitor 24p",
                new BigDecimal("800.00"),
                15,
                "SKU-999");

        Product product2 = new Product(
                "Mouse",
                "Gamer",
                new BigDecimal("150.00"),
                20,
                "SKU-2");

        List<Product> mockList = List.of(product1, product2);

        Mockito.when(productRepository.findAll()).thenReturn(mockList);

        // When
        List<ProductResponseDTO> result = productService.findAll();

        // Then
        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .extracting(ProductResponseDTO::name)
                .containsExactly("Monitor", "Mouse");

        Mockito.verify(productRepository, Mockito.times(1)).findAll();
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve retornar uma lista vazia quando não houver produtos no banco")
    void shouldReturnEmptyListWhenDatabaseIsEmpty() {
        // Given
        Mockito.when(productRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<ProductResponseDTO> result = productService.findAll();

        // Then
        assertThat(result)
                .isNotNull()
                .isEmpty();

        Mockito.verify(productRepository, Mockito.times(1)).findAll();
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve retornar a Entidade Produto quando o ID existir no banco")
    void shouldReturnProductEntityWhenIdExists() {
        // Given
        UUID productId = UUID.randomUUID();
        Product mockProduct = new Product("Teclado", "Mecânico", new BigDecimal("300.00"), 10, "SKU-123");

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

        // When
        Product result = productService.findEntityById(productId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(mockProduct.getName());

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar Entidade por ID inexistente")
    void shouldThrowExceptionWhenFindEntityByIdFails() {
        // Given
        UUID productId = UUID.randomUUID();
        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.findEntityById(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve retornar ProductResponseDTO quando o ID existir no banco")
    void shouldReturnProductResponseDTOWhenIdExists() {
        // Given
        UUID productId = UUID.randomUUID();
        Product mockProduct = new Product(
                "Mouse",
                "Mecânico",
                new BigDecimal("150.00"),
                20,
                "SKU-2");

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

        // When
        ProductResponseDTO result = productService.findById(productId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(mockProduct.getName());

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar DTO por ID inexistente")
    void shouldThrowExceptionWhenFindByIdFails() {
        // Given
        UUID productId = UUID.randomUUID();
        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.findById(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve inserir produto quando não houver conflito no banco")
    void shouldInsertProductWhenThereIsNotConflict() {
        // Given
        ProductRequestDTO toBeInsertedProduct = new ProductRequestDTO(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("100.00"),
                10,
                "SKU-123"
        );

        Product insertedProduct = new Product(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("100.00"),
                10,
                "SKU-123"
        );

        Mockito.when(productRepository.existsBySku(toBeInsertedProduct.sku())).thenReturn(false);
        Mockito.when(productRepository.save(Mockito.any(Product.class))).thenReturn(insertedProduct);

        // When
        ProductResponseDTO result = productService.insert(toBeInsertedProduct);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(toBeInsertedProduct.name());

        Mockito.verify(productRepository, Mockito.times(1)).existsBySku(toBeInsertedProduct.sku());
        Mockito.verify(productRepository, Mockito.times(1)).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar DataConflictException quando produto já estiver no banco")
    void shouldThrowDataConflictExceptionWhenProductAlreadyExists() {
        // Given
        ProductRequestDTO toBeInsertedProduct = new ProductRequestDTO(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("100.00"),
                10,
                "SKU-123"
        );

        Mockito.when(productRepository.existsBySku(toBeInsertedProduct.sku())).thenReturn(true);

        // When - Then
        assertThatThrownBy(() -> productService.insert(toBeInsertedProduct))
                .isInstanceOf(DataConflictException.class)
                .hasMessage("Já existe um produto cadastrado com o SKU: " + toBeInsertedProduct.sku());

        Mockito.verify(productRepository, Mockito.times(1)).existsBySku(toBeInsertedProduct.sku());
        Mockito.verify(productRepository, Mockito.never()).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }
}

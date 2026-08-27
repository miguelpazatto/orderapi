package unit.products.services;

import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.products.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.products.dtos.ProductResponseDTO;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
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

        Mockito.when(productRepository.save(Mockito.any(Product.class))).thenReturn(productToSave);

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

    @Test
    @DisplayName("Deve alterar o estoque de um produto quando ID existir")
    void shouldUpdateStockWhenIdExists() {
        // Given
        UUID productId = UUID.randomUUID();
        Integer newStock = 15;

        Product product = new Product(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("100.00"),
                10,
                "SKU-123"
        );

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Mockito.when(productRepository.save(Mockito.any(Product.class))).thenReturn(product);

        // When
        ProductResponseDTO result = productService.updateStock(productId, newStock);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(product.getName());

        assertThat(result.availableStock()).isEqualTo(newStock);

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.times(1)).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar atualizar estoque de ID inexistente")
    void shouldThrowExceptionWhenUpdatingStockOfNonExistingId() {
        // Given
        UUID productId = UUID.randomUUID();
        Integer newStock = 15;

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.updateStock(productId, newStock))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.never()).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve alterar o preço de um produto quando ID existir")
    void shouldUpdatePriceWhenIdExists() {
        // Given
        UUID productId = UUID.randomUUID();
        BigDecimal newPrice = new BigDecimal("250.00");

        Product product = new Product(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("100.00"),
                10,
                "SKU-123"
        );

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Mockito.when(productRepository.save(Mockito.any(Product.class))).thenReturn(product);

        // When
        ProductResponseDTO result = productService.updatePrice(productId, newPrice);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(product.getName());

        assertThat(result.price()).isEqualTo(newPrice);

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.times(1)).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar atualizar preço de ID inexistente")
    void shouldThrowExceptionWhenUpdatingPriceOfNonExistingId() {
        // Given
        UUID productId = UUID.randomUUID();
        BigDecimal newPrice = new BigDecimal("250.00");

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.updatePrice(productId, newPrice))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.never()).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve inativar o produto e salvar no banco quando ID existir")
    void shouldDeactivateProductWhenIdExists() {
        // Given
        UUID productId = UUID.randomUUID();

        Product product = new Product(
                "Mouse",
                "Gamer",
                new BigDecimal("100.00"),
                10,
                "SKU-1"
        );

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When
        productService.deactivate(productId);

        // Then
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.INACTIVE);

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException e não salvar ao inativar ID inexistente")
    void shouldThrowExceptionWhenDeactivatingNonExistingId() {
        // Given
        UUID productId = UUID.randomUUID();

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.deactivate(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.never()).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve ativar o produto como ACTIVE e salvar no banco quando ID existir e houver estoque")
    void shouldActivateProductAsActiveWhenIdExistsAndStockIsPositive() {
        // Given
        UUID productId = UUID.randomUUID();

        Product product = new Product(
                "Mouse",
                "Gamer",
                new BigDecimal("100.00"),
                10,
                "SKU-1"
        );

        product.deactivate();

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When
        productService.activate(productId);

        // Then
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.ACTIVE);

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve ativar o produto como OUT_OF_STOCK e salvar no banco quando ID existir e estoque for zero")
    void shouldActivateProductAsOutOfStockWhenIdExistsAndStockIsZero() {
        // Given
        UUID productId = UUID.randomUUID();

        Product product = new Product(
                "Teclado",
                "Mecânico",
                new BigDecimal("300.00"),
                0,
                "SKU-2"
        );

        product.deactivate();

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When
        productService.activate(productId);

        // Then
        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.times(1)).save(product);
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException e não salvar ao ativar ID inexistente")
    void shouldThrowExceptionWhenActivatingNonExistingId() {
        // Given
        UUID productId = UUID.randomUUID();

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.activate(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.never()).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve alterar os detalhes (nome e descrição) de um produto quando ID existir")
    void shouldUpdateDetailsWhenIdExists() {
        // Given
        UUID productId = UUID.randomUUID();
        String newName = "Monitor Ultrawide";
        String newDescription = "Monitor 29 polegadas WQHD";

        Product product = new Product(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("100.00"),
                10,
                "SKU-123"
        );

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Mockito.when(productRepository.save(Mockito.any(Product.class))).thenReturn(product);

        // When
        ProductResponseDTO result = productService.updateDetails(productId, newName, newDescription);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(newName);
        assertThat(result.description()).isEqualTo(newDescription);

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.times(1)).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao tentar atualizar detalhes de ID inexistente")
    void shouldThrowExceptionWhenUpdatingDetailsOfNonExistingId() {
        // Given
        UUID productId = UUID.randomUUID();
        String newName = "Monitor Ultrawide";
        String newDescription = "Monitor 29 polegadas WQHD";

        Mockito.when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When - Then
        assertThatThrownBy(() -> productService.updateDetails(productId, newName, newDescription))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Produto com ID " + productId + " não encontrado");

        Mockito.verify(productRepository, Mockito.times(1)).findById(productId);
        Mockito.verify(productRepository, Mockito.never()).save(Mockito.any(Product.class));
        Mockito.verifyNoMoreInteractions(productRepository);
    }
}

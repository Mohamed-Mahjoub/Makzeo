package ma.pfe.stock.product.infrastructure.web;

import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.security.SecurityConfig;
import ma.pfe.stock.common.web.GlobalExceptionHandler;
import ma.pfe.stock.product.application.dto.ProductView;
import ma.pfe.stock.product.application.service.ProductService;
import ma.pfe.stock.product.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class,
        properties = "app.security.jwt-secret=test-secret-with-at-least-32-characters!!")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"reference":"PRD-1","name":"Souris","categoryId":1,"minStock":2,"purchasePrice":10.5}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    private static ProductView view() {
        return new ProductView(1L, "PRD-1", "Souris", null, 1L, "Informatique", "2000000000077",
                UnitOfMeasure.PIECE, new BigDecimal("10.50"), BigDecimal.ZERO, 2, null, null, true,
                Instant.now(), Instant.now());
    }

    @Test
    void gestionnaireCanCreateProduct() throws Exception {
        when(productService.create(any())).thenReturn(view());

        mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GESTIONNAIRE")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.barcode").value("2000000000077"));
    }

    @Test
    void magasinierCannotCreateProduct() throws Exception {
        mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MAGASINIER")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void consultantCanReadProduct() throws Exception {
        when(productService.findById(1L)).thenReturn(view());

        mvc.perform(get("/api/products/1").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("PRD-1"));
    }

    @Test
    void unknownProductReturns404() throws Exception {
        when(productService.findById(9L)).thenThrow(new ResourceNotFoundException("Produit", 9L));

        mvc.perform(get("/api/products/9").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTANT"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        mvc.perform(post("/api/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"a b\",\"minStock\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}

package customer.ams_cap_bookshop.handlers;

import cds.gen.catalogservice.CatalogService;
import cds.gen.catalogservice.SubmitOrderContext;
import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import static cds.gen.catalogservice.CatalogService_.BOOKS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CatalogServiceTest {
    @Autowired
    private CatalogService.Application catalogService;

    @Test
    void catalogBooksAreReadableWithoutAuthentication() {
        // CatalogService has no @requires, so catalog reads are public
        Result result = catalogService.run(Select.from(BOOKS));
        assertEquals(true, result.rowCount() >= 5);
        assertTrue(result.stream().anyMatch(row -> "c7641340-a9be-4673-8dad-785a2505f46e".equals(row.get("ID"))));
    }

    @Test
    void catalogBooksAboveStockThresholdAreDiscounted() {
        Result result = catalogService.run(Select.from(BOOKS));
        assertEquals(true, result.rowCount() > 0);

        // the @After(READ) handler appends " (discounted)" to titles of books with stock > 200
        assertTrue(result.stream().allMatch(row -> {
            Object stock = row.get("stock");
            boolean aboveThreshold = stock != null && (int) stock > 200;
            boolean discounted = ((String) row.get("title")).endsWith(" (discounted)");
            return aboveThreshold == discounted;
        }));
    }

    @Test
    @WithMockUser(username = "user")
    void submitOrderDecreasesStockAndReturnsNewStock() {
        String ravenBookId = "c7641340-a9be-4673-8dad-785a2505f46e"; // The Raven, initial stock 333

        SubmitOrderContext.ReturnType result = catalogService.submitOrder(ravenBookId, 2);

        assertEquals(331, result.getStock());
    }
}

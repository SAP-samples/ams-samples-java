package customer.ams_cap_bookshop.handlers;

import cds.gen.adminservice.AdminService;
import cds.gen.adminservice.Authors;
import cds.gen.adminservice.Books;
import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;

import static cds.gen.adminservice.AdminService_.AUTHORS;
import static cds.gen.adminservice.AdminService_.BOOKS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AdminServiceHandlerTest {
    @Autowired
    private AdminService.Draft adminService;

    @Test
    @WithMockUser(username = "stock-manager")
    void createAuthorUnauthorized() {
        assertThrows(ServiceException.class, () -> {
            adminService.newDraft(Insert.into(AUTHORS).entry(Collections.emptyMap()));
        });
    }

    @Test
    @WithMockUser(username = "content-manager")
    public void createAuthor() {
        Authors author = Authors.create();

        author.setName("Carloz Ruiz Zafon");
        author.setDateOfBirth(java.time.LocalDate.of(1964, 9, 25));
        author.setPlaceOfBirth("Barcelona, Spain");

        Result result = adminService.run(Insert.into(AUTHORS).entry(author));
        assertEquals(1, result.rowCount());
    }

    @Test
    @WithMockUser(username = "stock-manager")
    void createBookAsStockManager() {
        Books book = Books.create();

        book.setTitle("The Tell-Tale Heart");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");  // Edgar Allan Poe
        book.setGenreId(16); // Mystery

        Result result = adminService.run(Insert.into(BOOKS).entry(book));
        assertEquals(1, result.rowCount());
    }

    @Test
    @WithMockUser(username = "content-manager")
    void createBookAsContentManager() {
        Books book = Books.create();

        book.setTitle("The Tell-Tale Heart");
        book.setAuthorId("4cf60975-300d-4dbe-8598-57b02e62bae2");  // Edgar Allan Poe
        book.setGenreId(16); // Mystery

        Result result = adminService.run(Insert.into(BOOKS).entry(book));
        assertEquals(1, result.rowCount());
    }

    @Test
    @WithMockUser(username = "stock-manager")
    void getBooksAsStockManager() {
        Result result = adminService.run(Select.from(BOOKS));
        assertEquals(true, result.rowCount() > 0);
    }

    @Test
    @WithMockUser(username = "content-manager")
    void getBooksAsContentManager() {
        Result result = adminService.run(Select.from(BOOKS));
        assertEquals(true, result.rowCount() > 0);
    }

    @Test
    @WithMockUser(username = "stock-manager-fiction")
    void getBooksAsStockManagerFiction() {
        Result result = adminService.run(Select.from(BOOKS));
        assertEquals(true, result.rowCount() > 0);

        // role restricted to Fantasy genre (genre_ID = 13) and Mystery genre (genre_ID = 16)
        assertTrue(result.stream().allMatch(row -> (int) row.get("genre_ID") == 13 || (int) row.get("genre_ID") == 16));
    }
}

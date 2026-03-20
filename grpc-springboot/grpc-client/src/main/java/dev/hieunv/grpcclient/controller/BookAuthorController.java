package dev.hieunv.grpcclient.controller;

import com.google.protobuf.Descriptors;
import dev.hieunv.grpcclient.service.BookAuthorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
public class BookAuthorController {

    private final BookAuthorService bookAuthorService;

    @GetMapping("/author/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public Map<Descriptors.FieldDescriptor, Object> getAuthor(@PathVariable("id") int authorId) {
        return bookAuthorService.getAuthor(authorId);
    }

    @GetMapping("books/{authorId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthor(@PathVariable("authorId") int authorId) throws InterruptedException {
        return bookAuthorService.getBooksByAuthor(authorId);
    }

    @GetMapping("books/expensive-book")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Map<Descriptors.FieldDescriptor, Object>> getExpensiveBook() throws InterruptedException {
        return bookAuthorService.getExpensiveBook();
    }

    @GetMapping("books/author/{gender}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthor(@PathVariable("gender") String gender) throws InterruptedException {
        return bookAuthorService.getBooksByAuthorGender(gender);
    }
}

package dev.hieunv.grpcclient.controller;

import com.google.protobuf.Descriptors;
import dev.hieunv.grpcclient.service.BookAuthorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequiredArgsConstructor
@RestController
public class BookAuthorController {

    private final BookAuthorService bookAuthorService;

    @GetMapping("/author/{id}")
    public Map<Descriptors.FieldDescriptor, Object> getAuthor(@PathVariable("id") int authorId) {
        return bookAuthorService.getAuthor(authorId);
    }
}

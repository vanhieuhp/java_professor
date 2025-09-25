package dev.hieunv.grpc;

import java.util.ArrayList;
import java.util.List;

public class TempDb {

    public static List<Author> getAuthorsFromTempDb() {
        List<Author> authors = new ArrayList<>();
        authors.add(Author.newBuilder().setAuthorId(1).setFirstName("Charles").setLastName("Dickens").setGender("Male").setBookId(1).build());
        authors.add(Author.newBuilder().setAuthorId(2).setFirstName("Jane").setLastName("Austen").setGender("Female").setBookId(2).build());
        authors.add(Author.newBuilder().setAuthorId(3).setFirstName("Mark").setLastName("Twain").setGender("Male").setBookId(3).build());
        authors.add(Author.newBuilder().setAuthorId(4).setFirstName("William").setLastName("Shakespeare").setGender("Male").setBookId(4).build());
        authors.add(Author.newBuilder().setAuthorId(5).setFirstName("Leo").setLastName("Tolstoy").setGender("Male").setBookId(5).build());
        authors.add(Author.newBuilder().setAuthorId(6).setFirstName("Virginia").setLastName("Woolf").setGender("Female").setBookId(6).build());
        authors.add(Author.newBuilder().setAuthorId(7).setFirstName("J.K.").setLastName("Rowling").setGender("Female").setBookId(7).build());
        authors.add(Author.newBuilder().setAuthorId(8).setFirstName("Agatha").setLastName("Christie").setGender("Female").setBookId(8).build());
        return authors;
    }

    public static List<Book> getBooksFromTempDb() {
        List<Book> books = new ArrayList<>();
        books.add(Book.newBuilder().setBookId(1).setTitle("Great Expectations").setPrice(10.99f).setPages(200).setAuthorId(1).build());
        books.add(Book.newBuilder().setBookId(2).setTitle("Pride and Prejudice").setPrice(12.99f).setPages(250).setAuthorId(2).build());
        books.add(Book.newBuilder().setBookId(3).setTitle("The Adventures of Tom Sawyer").setPrice(14.99f).setPages(300).setAuthorId(3).build());
        books.add(Book.newBuilder().setBookId(4).setTitle("Hamlet").setPrice(16.99f).setPages(350).setAuthorId(4).build());
        books.add(Book.newBuilder().setBookId(5).setTitle("War and Peace").setPrice(18.99f).setPages(1200).setAuthorId(5).build());
        books.add(Book.newBuilder().setBookId(6).setTitle("Mrs. Dalloway").setPrice(13.99f).setPages(200).setAuthorId(6).build());
        books.add(Book.newBuilder().setBookId(7).setTitle("Harry Potter and the Philosopher's Stone").setPrice(15.99f).setPages(320).setAuthorId(7).build());
        books.add(Book.newBuilder().setBookId(8).setTitle("Murder on the Orient Express").setPrice(11.99f).setPages(256).setAuthorId(8).build());
        return books;
    }
}

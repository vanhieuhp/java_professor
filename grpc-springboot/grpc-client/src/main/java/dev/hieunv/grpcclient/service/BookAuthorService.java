package dev.hieunv.grpcclient.service;

import com.google.protobuf.Descriptors;

import java.util.List;
import java.util.Map;

public interface BookAuthorService {

    Map<Descriptors.FieldDescriptor, Object> getAuthor(int authorId);

    List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthor(int authorId) throws InterruptedException;

    Map<String, Map<Descriptors.FieldDescriptor, Object>> getExpensiveBook() throws InterruptedException;

    List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthorGender(String gender) throws InterruptedException;
}

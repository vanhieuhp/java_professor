package dev.hieunv.grpcclient.service;

import com.google.protobuf.Descriptors;

import java.util.Map;

public interface BookAuthorService {

    Map<Descriptors.FieldDescriptor, Object> getAuthor(int authorId);
}

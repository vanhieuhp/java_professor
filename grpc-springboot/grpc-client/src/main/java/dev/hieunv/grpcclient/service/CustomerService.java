package dev.hieunv.grpcclient.service;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.hieunv.grpc.Customer;

public interface CustomerService {

    Customer getCustomer(int id) throws InvalidProtocolBufferException;

    Object getListCustomer() throws InterruptedException;
}

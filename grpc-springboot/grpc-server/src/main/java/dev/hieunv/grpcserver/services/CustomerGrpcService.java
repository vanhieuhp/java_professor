package dev.hieunv.grpcserver.services;

import com.google.protobuf.Empty;
import dev.hieunv.grpc.CustomError;
import dev.hieunv.grpc.Customer;
import dev.hieunv.grpc.CustomerDetails;
import dev.hieunv.grpc.CustomerServiceGrpc;
import dev.hieunv.grpcserver.exceptions.CustomerNotFoundException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.stream.IntStream;

@Slf4j
@GrpcService
public class CustomerGrpcService extends CustomerServiceGrpc.CustomerServiceImplBase {

    @Override
    public void getCustomer(Customer request, StreamObserver<Customer> responseObserver) {


        if (request.getId() == 2) {
            throw new CustomerNotFoundException("Customer with ID 2 not found");
        } else {
            responseObserver.onNext(Customer.newBuilder().setName("hieunv").setEmail("hieunv.dev@gmail").build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getCustomers(Empty request, StreamObserver<CustomerDetails> responseObserver) {
        IntStream.range(1, 10)
                .forEach(number -> {
                    if (!(number % 2 == 0)) {
                        CustomerDetails notFound = CustomerDetails.newBuilder()
                                .setCustomError(CustomError.newBuilder()
                                        .setErrorType("NOT_FOUND")
                                        .setMessage(String.format("customer with Id %d not found", number))
                                        .build())
                                .build();
                        responseObserver.onNext(notFound);
                    } else {
                        CustomerDetails hieunv = CustomerDetails.newBuilder()
                                .setCustomer(Customer.newBuilder()
                                        .setName("hieunv")
                                        .setEmail("hieunv.dev@gmail")
                                        .setId(number)
                                        .build())
                                .build();
                        responseObserver.onNext(hieunv);
                    }
                });

        responseObserver.onCompleted();

    }
}

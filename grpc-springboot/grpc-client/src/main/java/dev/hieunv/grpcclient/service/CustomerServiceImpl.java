package dev.hieunv.grpcclient.service;

import com.google.protobuf.Any;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;
import com.google.rpc.Status;
import dev.hieunv.grpc.CustomError;
import dev.hieunv.grpc.Customer;
import dev.hieunv.grpc.CustomerDetails;
import dev.hieunv.grpc.CustomerServiceGrpc;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService{

    @GrpcClient("grpc-devhieunv-service")
    CustomerServiceGrpc.CustomerServiceBlockingStub syncClient;

    @GrpcClient("grpc-devhieunv-service")
    CustomerServiceGrpc.CustomerServiceStub asyncClient;

    @Override
    public Customer getCustomer(int id) throws InvalidProtocolBufferException {
        try {
            Customer customer = syncClient.getCustomer(Customer.newBuilder().setId(id).build());
            System.out.println(customer);
        } catch (StatusRuntimeException e) {
            Status status = StatusProto.fromThrowable(e);
            assert status != null;
            String notFound = Code.forNumber(status.getCode()).toString();
            System.out.println(notFound);
            for (Any any : status.getDetailsList()) {
                if (any.is(CustomError.class)) {
                    CustomError unpack = any.unpack(CustomError.class);
                    System.out.println(unpack);
                }
            }
        }
        return null;
    }

    @Override
    public Object getListCustomer() throws InterruptedException {

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        asyncClient.getCustomers(Empty.newBuilder().build(), new StreamObserver<CustomerDetails>() {
            @Override
            public void onNext(CustomerDetails customerDetails) {
                switch (customerDetails.getMessageCase()) {
                    case CUSTOMERROR -> System.out.println(customerDetails.getCustomError());
                    case CUSTOMER -> System.out.println(customerDetails.getCustomer());
                }
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {
                countDownLatch.countDown();
            }
        });

        countDownLatch.await();
        return null;
    }
}

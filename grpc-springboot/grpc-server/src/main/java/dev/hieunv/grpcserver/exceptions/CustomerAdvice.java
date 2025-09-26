package dev.hieunv.grpcserver.exceptions;

import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import dev.hieunv.grpc.CustomError;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

@GrpcAdvice
public class CustomerAdvice {

    @GrpcExceptionHandler(CustomerNotFoundException.class)
    public StatusRuntimeException handleCustomerNotFoundException(CustomerNotFoundException exception) {
        Status status = Status.newBuilder()
                .setCode(Code.NOT_FOUND_VALUE)
                .addDetails(Any.pack(CustomError.newBuilder()
                        .setMessage(exception.getMessage())
                        .setErrorType("NOT_FOUND")
                        .build())).build();

        return StatusProto.toStatusRuntimeException(status);
    }
}

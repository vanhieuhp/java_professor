package dev.hieunv.riskassessment.event;

/** Đường sự kiện đi vào. Ghi lại để sau này trả lời được "sự kiện này vào bằng đường nào". */
public enum CoreEventSource {

    /** Đường chính. */
    KAFKA,

    /**
     * Đường dự phòng khi Kafka chết. Dùng chung cổng chống trùng với Kafka, nên cùng một
     * sự kiện vào bằng cả hai đường vẫn chỉ có hiệu ứng một lần.
     */
    REST
}

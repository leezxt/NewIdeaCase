package com.newideacase.platform.catalog.application;

import com.newideacase.platform.catalog.domain.Product;
import com.newideacase.platform.catalog.domain.ProductCursor;
import com.newideacase.platform.shared.error.BadRequestException;
import java.nio.ByteBuffer;
import java.util.Base64;
import org.bson.types.ObjectId;

final class ProductCursorCodec {

    private static final int PAYLOAD_SIZE = Long.BYTES + 12;

    private ProductCursorCodec() {
    }

    static String encode(Product product) {
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_SIZE);
        payload.putLong(product.createdAt().toEpochMilli());
        payload.put(new ObjectId(product.id()).toByteArray());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
    }

    static ProductCursor decode(String value) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(value);
            if (payload.length != PAYLOAD_SIZE) {
                throw new IllegalArgumentException("Unexpected cursor size");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            long timestamp = buffer.getLong();
            byte[] objectId = new byte[12];
            buffer.get(objectId);
            return new ProductCursor(java.time.Instant.ofEpochMilli(timestamp), new ObjectId(objectId).toHexString());
        } catch (RuntimeException exception) {
            throw new BadRequestException("Invalid product cursor", exception);
        }
    }
}

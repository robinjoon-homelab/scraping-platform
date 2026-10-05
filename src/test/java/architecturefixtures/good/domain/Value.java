package architecturefixtures.good.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class Value implements Serializable {
    UUID id;
    Instant createdAt;
}

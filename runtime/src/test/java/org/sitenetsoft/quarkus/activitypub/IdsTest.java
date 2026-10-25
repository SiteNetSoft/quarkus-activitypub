package org.sitenetsoft.quarkus.activitypub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void ulidsAreCrockfordAndStrictlyIncreasing() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Ids.ulid());
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            assertEquals(26, id.length(), id);
            assertTrue(id.matches("[0-9A-HJKMNP-TV-Z]{26}"), id);
            if (i > 0) {
                assertTrue(ids.get(i - 1).compareTo(id) < 0, ids.get(i - 1) + " !< " + id);
            }
        }
    }

    @Test
    void timestampPrefixEncodesCurrentMillis() {
        long before = System.currentTimeMillis();
        String id = Ids.ulid();
        long decoded = 0;
        for (char c : id.substring(0, 10).toCharArray()) {
            decoded = decoded * 32 + "0123456789ABCDEFGHJKMNPQRSTVWXYZ".indexOf(c);
        }
        assertTrue(decoded >= before && decoded <= System.currentTimeMillis() + 1, Long.toString(decoded));
    }
}

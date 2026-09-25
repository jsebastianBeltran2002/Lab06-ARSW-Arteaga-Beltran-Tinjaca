package edu.eci.arsw.collabboard.infrastructure.web.ws;

import java.time.Instant;

/** Private rejection notice sent back to the client whose event was not accepted. */
public record BoardEventError(
        Instant timestamp,
        String code,
        String message
) {
}

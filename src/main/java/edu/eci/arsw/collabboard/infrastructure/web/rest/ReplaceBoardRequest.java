package edu.eci.arsw.collabboard.infrastructure.web.rest;

import edu.eci.arsw.collabboard.domain.model.BoardElement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceBoardRequest(
        @NotBlank(message = "name is required") String name,
        @NotNull(message = "elements are required") List<@Valid BoardElement> elements
) {
}

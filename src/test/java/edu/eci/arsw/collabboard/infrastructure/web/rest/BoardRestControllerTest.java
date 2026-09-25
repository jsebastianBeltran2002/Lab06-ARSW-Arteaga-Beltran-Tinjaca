package edu.eci.arsw.collabboard.infrastructure.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BoardRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateReadAndReplaceBoard() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest("Architecture Session"));

        String response = mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Architecture Session"))
                .andReturn().getResponse().getContentAsString();

        String boardId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/boards/" + boardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(boardId));

        String replaceBody = """
                {"name":"Renamed Session","elements":[
                  {"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":10,"height":5,"text":""}
                ]}
                """;

        mockMvc.perform(put("/api/boards/" + boardId)
                        .contentType("application/json")
                        .content(replaceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Session"))
                .andExpect(jsonPath("$.elements[0].id").value("el-1"));
    }

    @Test
    void shouldReturnUniformApiErrorWhenBoardDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/boards/missing-board"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/boards/missing-board"));
    }

    @Test
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest(""));

        mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturnInvalidInputWhenReplacingWithAnInvalidElement() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest("Architecture Session"));

        String response = mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String boardId = objectMapper.readTree(response).get("id").asText();

        String replaceBody = """
                {"name":"Renamed Session","elements":[
                  {"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":-10,"height":5,"text":""}
                ]}
                """;

        mockMvc.perform(put("/api/boards/" + boardId)
                        .contentType("application/json")
                        .content(replaceBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void shouldReturnInvalidInputWhenConnectorReferencesMissingElement() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest("Architecture Session"));

        String response = mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String boardId = objectMapper.readTree(response).get("id").asText();

        String replaceBody = """
                {"name":"Renamed Session","elements":[
                  {"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":10,"height":5,"text":""},
                  {"id":"c-1","type":"CONNECTOR","x":0,"y":0,"width":0,"height":0,"sourceId":"el-1","targetId":"missing"}
                ]}
                """;

        mockMvc.perform(put("/api/boards/" + boardId)
                        .contentType("application/json")
                        .content(replaceBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void shouldReplaceBoardWithValidConnectorBetweenExistingElements() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest("Architecture Session"));

        String response = mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String boardId = objectMapper.readTree(response).get("id").asText();

        String replaceBody = """
                {"name":"Renamed Session","elements":[
                  {"id":"el-1","type":"RECTANGLE","x":1,"y":2,"width":10,"height":5,"text":""},
                  {"id":"el-2","type":"TEXT","x":20,"y":20,"width":10,"height":5,"text":"note"},
                  {"id":"c-1","type":"CONNECTOR","x":0,"y":0,"width":0,"height":0,"sourceId":"el-1","targetId":"el-2"}
                ]}
                """;

        mockMvc.perform(put("/api/boards/" + boardId)
                        .contentType("application/json")
                        .content(replaceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[2].type").value("CONNECTOR"))
                .andExpect(jsonPath("$.elements[2].sourceId").value("el-1"))
                .andExpect(jsonPath("$.elements[2].targetId").value("el-2"));
    }

    @Test
    void shouldReturnBadRequestWhenReplacingWithoutElements() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest("Architecture Session"));

        String response = mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String boardId = objectMapper.readTree(response).get("id").asText();

        String replaceBody = """
                {"name":"Renamed Session"}
                """;

        mockMvc.perform(put("/api/boards/" + boardId)
                        .contentType("application/json")
                        .content(replaceBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldDeleteExistingBoard() throws Exception {
        String createBody = objectMapper.writeValueAsString(new CreateBoardRequest("Architecture Session"));

        String response = mockMvc.perform(post("/api/boards")
                        .contentType("application/json")
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String boardId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(delete("/api/boards/" + boardId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/boards/" + boardId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownBoard() throws Exception {
        mockMvc.perform(delete("/api/boards/missing-board"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOARD_NOT_FOUND"));
    }
}

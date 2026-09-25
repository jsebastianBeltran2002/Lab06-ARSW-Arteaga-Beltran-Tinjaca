package edu.eci.arsw.collabboard.infrastructure.web.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.arsw.collabboard.application.event.BoardEvent;
import edu.eci.arsw.collabboard.application.event.BoardEventPayload;
import edu.eci.arsw.collabboard.application.event.BoardEventType;
import edu.eci.arsw.collabboard.application.service.BoardApplicationService;
import edu.eci.arsw.collabboard.domain.model.Board;
import edu.eci.arsw.collabboard.domain.model.BoardElement;
import edu.eci.arsw.collabboard.domain.model.ElementType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end check of the live path with real STOMP clients over /ws:
 * SEND /app/boards/{id}/events → validate + apply → /topic/boards/{id}.
 * Mirrors the lab's three-window demo: two clients on the same Board, one on another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BoardRealtimeIntegrationTest {

    private static final long RECEIVE_TIMEOUT_MS = 3000;
    private static final long SILENCE_TIMEOUT_MS = 700;

    @LocalServerPort
    private int port;

    @Autowired
    private BoardApplicationService boardService;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Same Spring Boot ObjectMapper the server uses (ISO-8601 Instant support).
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
        sessions.forEach(StompSession::disconnect);
        stompClient.stop();
    }

    @Test
    void acceptedEventReachesEveryClientOfTheSameBoardAndNoOtherBoard() throws Exception {
        Board boardA = boardService.createBoard("Board A");
        Board boardB = boardService.createBoard("Board B");

        StompSession browserA = connect();
        StompSession browserB = connect();
        StompSession browserC = connect();
        BlockingQueue<BoardEvent> inboxA = subscribe(browserA, "/topic/boards/" + boardA.id(), BoardEvent.class);
        BlockingQueue<BoardEvent> inboxB = subscribe(browserB, "/topic/boards/" + boardA.id(), BoardEvent.class);
        BlockingQueue<BoardEvent> inboxC = subscribe(browserC, "/topic/boards/" + boardB.id(), BoardEvent.class);
        awaitSubscriptions();

        BoardElement rectangle = new BoardElement("rect-1", ElementType.RECTANGLE, 10, 10, 120, 80, "", null, null);
        BoardEvent created = event(boardA.id(), BoardEventType.ELEMENT_CREATED, BoardEventPayload.ofElement(rectangle));
        browserA.send("/app/boards/" + boardA.id() + "/events", created);

        BoardEvent receivedByA = inboxA.poll(RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        BoardEvent receivedByB = inboxB.poll(RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(receivedByA, "sender must receive the accepted event");
        assertNotNull(receivedByB, "other client of the same board must receive the event");
        assertEquals(created.eventId(), receivedByB.eventId());
        assertEquals(rectangle, receivedByB.payload().element());
        assertNull(inboxC.poll(SILENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS), "another board must not receive the event");

        // The accepted transition is also the REST snapshot.
        assertEquals(List.of(rectangle), boardService.getBoard(boardA.id()).elements());
        assertEquals(List.of(), boardService.getBoard(boardB.id()).elements());
    }

    @Test
    void rejectedEventIsNotBroadcastAndOnlyTheSenderIsNotified() throws Exception {
        Board board = boardService.createBoard("Board");

        StompSession sender = connect();
        StompSession peer = connect();
        BlockingQueue<BoardEvent> senderTopic = subscribe(sender, "/topic/boards/" + board.id(), BoardEvent.class);
        BlockingQueue<BoardEvent> peerTopic = subscribe(peer, "/topic/boards/" + board.id(), BoardEvent.class);
        BlockingQueue<BoardEventError> senderErrors = subscribe(sender, "/user/queue/errors", BoardEventError.class);
        BlockingQueue<BoardEventError> peerErrors = subscribe(peer, "/user/queue/errors", BoardEventError.class);
        awaitSubscriptions();

        BoardEvent moveGhost = event(board.id(), BoardEventType.ELEMENT_MOVED, BoardEventPayload.ofPosition("ghost", 1, 1));
        sender.send("/app/boards/" + board.id() + "/events", moveGhost);

        BoardEventError error = senderErrors.poll(RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(error, "sender must be told its event was rejected");
        assertEquals("INVALID_EVENT", error.code());
        assertNull(peerErrors.poll(SILENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNull(senderTopic.poll(SILENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertNull(peerTopic.poll(SILENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private StompSession connect() throws Exception {
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { })
                .get(RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        sessions.add(session);
        return session;
    }

    private static <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) {
        BlockingQueue<T> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add(type.cast(payload));
            }
        });
        return inbox;
    }

    /** SUBSCRIBE frames are processed asynchronously by the simple broker; give them time to register. */
    private static void awaitSubscriptions() throws InterruptedException {
        Thread.sleep(300);
    }

    private static BoardEvent event(String boardId, BoardEventType type, BoardEventPayload payload) {
        return new BoardEvent(UUID.randomUUID().toString(), boardId, type, "client-it", Instant.now(), payload);
    }
}

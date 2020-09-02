package ch.so.agi.ili2gpkgws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

public abstract class IntegrationTests {
    Logger logger = LoggerFactory.getLogger(IntegrationTests.class);

    @LocalServerPort
    protected String port;
    
    @Value("#{servletContext.contextPath}")
    protected String servletContextPath;

    public class ClientSocketHandler implements WebSocketHandler {
        Logger logger = LoggerFactory.getLogger(ClientSocketHandler.class);

        private WebSocketSession webSocketSession;
        private String returnedMessage;

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            webSocketSession = session;
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
            String result = message.getPayload().toString();
            this.returnedMessage = result;
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            logger.error("Got a handleTransportError: " + exception.getMessage());
            exception.printStackTrace();
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }

        public boolean isConnected() {
            return webSocketSession != null;
        }

        public void sendMessage(Object msg) throws Exception {
            if (msg instanceof File) {
                byte[] fileContent = Files.readAllBytes(((File) msg).toPath());
                webSocketSession.sendMessage(new BinaryMessage(fileContent));
            } else {
                webSocketSession.sendMessage(new TextMessage(msg.toString()));
            }
        }

        public void closeConnection() throws IOException {
            if (isConnected()) {
                webSocketSession.close();
            }
        }
        
        public String getMessage() {
            return this.returnedMessage;
        }
    }

    @Test
    public void validation_Ok_ili1() throws Exception {
        String endpoint = "ws://localhost:" + port + "/socket";

        StandardWebSocketClient client = new StandardWebSocketClient();
        ClientSocketHandler clientHandler = new ClientSocketHandler();
        client.doHandshake(clientHandler, endpoint);

        Thread.sleep(2000);
        assertTrue(clientHandler.isConnected());
        
        File file = new File("src/test/data/GKSO11_Balsthal.itf");
        clientHandler.sendMessage(file);
        clientHandler.sendMessage(file.getName());

        Thread.sleep(10000);
        
        String returnedMessage = clientHandler.getMessage();
        assertTrue(returnedMessage.contains("...import done"));        
    }
    
    @Test
    public void validation_Fail_ili1() throws Exception {
        String endpoint = "ws://localhost:" + port + "/socket";

        StandardWebSocketClient client = new StandardWebSocketClient();
        ClientSocketHandler clientHandler = new ClientSocketHandler();
        client.doHandshake(clientHandler, endpoint);

        Thread.sleep(2000);
        assertTrue(clientHandler.isConnected());
        
        File file = new File("src/test/data/fubar.itf");
        clientHandler.sendMessage(file);
        clientHandler.sendMessage(file.getName());

        Thread.sleep(10000);
        
        String returnedMessage = clientHandler.getMessage();
        System.out.println(returnedMessage);
        assertTrue(returnedMessage.contains("could not parse file: fubar.itf"));
    }
}

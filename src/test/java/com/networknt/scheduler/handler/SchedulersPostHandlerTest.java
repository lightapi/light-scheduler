
package com.networknt.scheduler.handler;

import com.networknt.client.Http2Client;
import com.networknt.client.simplepool.SimpleConnectionState;
import com.networknt.exception.ClientException;
import io.undertow.UndertowOptions;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.HttpString;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.OptionMap;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Ignore
public class SchedulersPostHandlerTest {
    @ClassRule
    public static TestServer server = TestServer.getInstance();

    static final Logger logger = LoggerFactory.getLogger(SchedulersPostHandlerTest.class);
    static final boolean enableHttp2 = server.getServerConfig().isEnableHttp2();
    static final boolean enableHttps = server.getServerConfig().isEnableHttps();
    static final int httpPort = server.getServerConfig().getHttpPort();
    static final int httpsPort = server.getServerConfig().getHttpsPort();
    static final String url = enableHttps ? "https://localhost:" + httpsPort : "http://localhost:" + httpPort;
    static final String JSON_MEDIA_TYPE = "application/json";

    @Test
    public void testSchedulersPostHandlerTest() throws ClientException {

        final Http2Client client = Http2Client.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);
        final SimpleConnectionState.ConnectionToken connectionToken;
        try {
            if (enableHttps) {
                connectionToken = client.borrow(new URI(url), Http2Client.WORKER, client.getDefaultXnioSsl(),
                        Http2Client.BUFFER_POOL,
                        enableHttp2 ? OptionMap.create(UndertowOptions.ENABLE_HTTP2, true) : OptionMap.EMPTY);
            } else {
                connectionToken = client.borrow(new URI(url), Http2Client.WORKER, Http2Client.BUFFER_POOL,
                        OptionMap.EMPTY);
            }
        } catch (Exception e) {
            throw new ClientException(e);
        }
        final ClientConnection connection = (ClientConnection) connectionToken.connection().getRawConnection();
        final AtomicReference<ClientResponse> reference = new AtomicReference<>();
        String requestUri = "/schedulers";
        try {
            ClientRequest request = new ClientRequest().setPath(requestUri).setMethod(Methods.POST);

            request.getRequestHeaders().put(Headers.CONTENT_TYPE, JSON_MEDIA_TYPE);
            request.getRequestHeaders().put(Headers.TRANSFER_ENCODING, "chunked");
            // customized header parameters
            request.getRequestHeaders().put(new HttpString("host"), "localhost");
            connection.sendRequest(request,
                    client.createClientCallback(reference, latch, "{\"content\": \"request body to be replaced\"}"));

            latch.await();
        } catch (Exception e) {
            logger.error("Exception: ", e);
            throw new ClientException(e);
        } finally {
            client.restore(connectionToken);
        }
        String body = reference.get().getAttachment(Http2Client.RESPONSE_BODY);
        Assert.assertNull(body);
    }
}

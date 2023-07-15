package mine.block.spotify.server;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.suppergerrie2.spotiforge.SpotiForge;
import mine.block.spotify.SpotifyHandler;
import mine.block.spotify.SpotifyUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.ParseException;
import org.slf4j.Logger;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CallbackHandler implements HttpHandler {
    private final HttpServer server;
    private static final Logger LOGGER = LogUtils.getLogger();

    public CallbackHandler(HttpServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        LOGGER.info(requestMethod + " " + exchange.getRequestURI());
        if (requestMethod.equalsIgnoreCase("GET")) {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try(OutputStream responseBody = exchange.getResponseBody(); InputStream page = SpotifyUtils.loadHTMLFile("callback")) {
                IOUtils.copy(page, responseBody);
            }

            LOGGER.info("Captured callback. Parsing oauth2 code.");

            var queries = SpotifyUtils.queryToMap(exchange.getRequestURI().getQuery());
            var code = queries.get("code");
            LOGGER.info(code);

            if (SpotifyHandler.SPOTIFY_API == null) {
                LOGGER.error("Spotify instance is null! Presend should have occurred!");
                System.exit(1);
            }

            AuthorizationCodeCredentials credentials = null;
            try {
                LOGGER.info("Marking credentials.");
                credentials = SpotifyHandler.SPOTIFY_API.authorizationCode(code).build().execute();
                LOGGER.info("Marked credentials.");
            } catch (SpotifyWebApiException | ParseException e) {
                LOGGER.info("Failed to setup Spotify - " + e);
                System.exit(1);
            }

            LOGGER.info("Setting credentials to spotify api instance.");
            SpotifyHandler.SPOTIFY_API.setAccessToken(credentials.getAccessToken());
            SpotifyHandler.SPOTIFY_API.setRefreshToken(credentials.getRefreshToken());

            LOGGER.info("Saving credentials.");
            SpotiForge.SPOTIFY_CONFIG.put("token", credentials.getAccessToken());
            SpotiForge.SPOTIFY_CONFIG.put("refresh-token", credentials.getRefreshToken());
            SpotiForge.SPOTIFY_CONFIG.put("version", SpotiForge.VERSION);
            LOGGER.info("Saved Credentials.");

            server.stop(15);
            LOGGER.info("Stopping server in 15 max-secs");
        } else {
            new NotImplementedHandler().handle(exchange);
        }
    }
}

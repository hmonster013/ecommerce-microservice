package org.de013.paymentservice.gateway.stripe;

import com.stripe.exception.ApiConnectionException;
import com.stripe.net.HttpClient;
import com.stripe.net.HttpHeaders;
import com.stripe.net.StripeRequest;
import com.stripe.net.StripeResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Fixed-length streaming prevents HttpURLConnection from replaying an ambiguous POST. */
final class StripeMutationHttpClient extends HttpClient {
    @Override
    public StripeResponse request(StripeRequest request) throws ApiConnectionException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) (request.options().getConnectionProxy() == null
                    ? request.url().openConnection() : request.url().openConnection(request.options().getConnectionProxy()));
            connection.setConnectTimeout(request.options().getConnectTimeout());
            connection.setReadTimeout(request.options().getReadTimeout());
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            for (var header : request.headers().map().entrySet())
                connection.setRequestProperty(header.getKey(), String.join(",", header.getValue()));
            connection.setRequestProperty("User-Agent", buildUserAgentString());
            connection.setRequestMethod(request.method().name());
            if (request.content() != null) {
                byte[] body = request.content().byteArrayContent();
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", request.content().contentType());
                connection.setFixedLengthStreamingMode(body.length);
                try (var output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            var headers = HttpHeaders.of(connection.getHeaderFields());
            var input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String body = "";
            if (input != null) try (input) { body = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            return new StripeResponse(status, headers, body);
        } catch (IOException e) {
            throw new ApiConnectionException("Stripe mutation outcome is unknown", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

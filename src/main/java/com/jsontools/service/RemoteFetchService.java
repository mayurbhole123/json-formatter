package com.jsontools.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Backs the "Load from URL" button.
 *
 * <p>The server fetches a URL the user supplies, so this is a server-side
 * request forgery surface. Every hop is checked against the host rules below,
 * redirects are followed manually so each new target is re-checked, and the
 * response is capped so a huge file cannot exhaust the heap.
 */
@Service
public class RemoteFetchService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public String fetch(String rawUrl) throws IOException, InterruptedException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Enter a URL to load.");
        }
        URI uri = parse(rawUrl.trim());

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkAllowed(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json, application/xml, text/*;q=0.9, */*;q=0.8")
                    .header("User-Agent", "json-formatter/1.0")
                    .GET()
                    .build();

            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalArgumentException(
                                "The server returned a redirect without a target."));
                response.body().close();
                uri = uri.resolve(location);
                continue;
            }
            if (status != 200) {
                response.body().close();
                throw new IllegalArgumentException("The server responded with HTTP " + status + ".");
            }
            try (InputStream body = response.body()) {
                return readCapped(body);
            }
        }
        throw new IllegalArgumentException("Too many redirects (more than " + MAX_REDIRECTS + ").");
    }

    private URI parse(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            if (uri.getScheme() == null) {
                // Bare "example.com/data.json" is a reasonable thing to paste.
                uri = new URI("https://" + rawUrl);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That is not a valid URL: " + e.getReason());
        }
    }

    private void checkAllowed(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs can be loaded.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("The URL has no host.");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("The host " + host + " could not be resolved.");
        }
        // Refuse anything pointing back at this machine or into a private network.
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new IllegalArgumentException(
                        "For safety, only public internet addresses can be loaded - " + host
                                + " resolves to a private or local address.");
            }
        }
    }

    private String readCapped(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IllegalArgumentException(
                        "The response is larger than the " + (MAX_BYTES / (1024 * 1024)) + " MB limit.");
            }
            out.write(buffer, 0, read);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}

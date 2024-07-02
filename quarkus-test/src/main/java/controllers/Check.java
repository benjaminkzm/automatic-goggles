package controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.horstmann.codecheck.Util;

import models.CodeCheck;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

@Path("/check")
public class Check {
    private static final Logger LOGGER = Logger.getLogger(Check.class);
    @Inject
    private CodeCheck codeCheck;

    @POST
    @Path("/html")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public CompletableFuture<Response> checkHTML(@javax.ws.rs.core.Context javax.ws.rs.core.HttpHeaders headers,
                                                  @javax.ws.rs.core.Context javax.ws.rs.core.Request request,
                                                  @javax.ws.rs.core.FormParam Map<String, String[]> params) throws IOException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String ccid = null;
                String repo = "ext";
                String problem = "";
                Map<Path, String> submissionFiles = new TreeMap<>();

                for (String key : params.keySet()) {
                    String value = params.get(key)[0];
                    if (key.equals("repo"))
                        repo = value;
                    else if (key.equals("problem"))
                        problem = value;
                    else if (key.equals("ccid"))
                        ccid = value;
                    else
                        submissionFiles.put(Paths.get(key), value);
                }
                if (ccid == null) {
                    ccid = Util.createPronouncableUID();
                }
                long startTime = System.nanoTime();
                String report = codeCheck.run("html", repo, problem, ccid, submissionFiles);
                double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
                if (report == null || report.length() == 0) {
                    report = String.format("Timed out after %5.0f seconds\n", elapsed);
                }

                return Response.ok(report).cookie(new NewCookie("ccid", ccid)).type(MediaType.TEXT_HTML).build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
        });
    }

    @POST
    @Path("/run")
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_JSON})
    public CompletableFuture<Response> run(@javax.ws.rs.core.Context javax.ws.rs.core.HttpHeaders headers,
                                           @javax.ws.rs.core.Context javax.ws.rs.core.Request request,
                                           @javax.ws.rs.core.FormParam Map<String, String[]> params,
                                           @MultipartForm FormDataMultiPart formData) throws IOException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<Path, String> submissionFiles = new TreeMap<>();
                String contentType = headers.getMediaType().toString();
                if (MediaType.APPLICATION_FORM_URLENCODED.equals(contentType)) {
                    for (String key : params.keySet()) {
                        String value = params.get(key)[0];
                        submissionFiles.put(Paths.get(key), value);
                    }
                } else if (MediaType.MULTIPART_FORM_DATA.equals(contentType)) {
                    Iterator<BodyPart> it = formData.getBodyParts().iterator();
                    while (it.hasNext()) {
                        BodyPart bp = it.next();
                        String key = bp.getContentDisposition().getFileName();
                        if (key == null)
                            key = bp.getName();
                        String value = bp.getEntityAs(String.class);
                        submissionFiles.put(Paths.get(key), value);
                    }
                } else if (MediaType.APPLICATION_JSON.equals(contentType)) {
                    JsonNode node = formData.getBodyPart(0).getEntityAs(JsonNode.class);
                    for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
                        Map.Entry<String, JsonNode> entry = it.next();
                        String key = entry.getKey();
                        String value = entry.getValue().asText();
                        submissionFiles.put(Paths.get(key), value);
                    }
                }

                String ccid = null;
                String repo = "ext";
                String problem = "";
                if (params.containsKey("repo"))
                    repo = params.get("repo")[0];
                if (params.containsKey("problem"))
                    problem = params.get("problem")[0];
                if (params.containsKey("ccid"))
                    ccid = params.get("ccid")[0];
                if (ccid == null) {
                    ccid = Util.createPronouncableUID();
                }
                long startTime = System.nanoTime();
                String report = codeCheck.run("run", repo, problem, ccid, submissionFiles);
                double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
                if (report == null || report.length() == 0) {
                    report = String.format("Timed out after %5.0f seconds\n", elapsed);
                }

                return Response.ok(report).cookie(new NewCookie("ccid", ccid)).type(MediaType.TEXT_HTML).build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
        });
    }

    @POST
    @Path("/checkNJS")
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletableFuture<Response> checkNJS(@javax.ws.rs.core.Context javax.ws.rs.core.HttpHeaders headers,
                                                @javax.ws.rs.core.Context javax.ws.rs.core.Request request,
                                                @javax.ws.rs.core.FormParam Map<String, String[]> params,
                                                String body) throws IOException, InterruptedException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode node = JsonNodeFactory.instance.objectNode();
                if (body != null && !body.isEmpty()) {
                    node = Util.MAPPER.readTree(body);
                }

                String ccid = null;
                String repo = "ext";
                String problem = "";
                if (params.containsKey("repo"))
                    repo = params.get("repo")[0];
                if (params.containsKey("problem"))
                    problem = params.get("problem")[0];
                if (params.containsKey("ccid"))
                    ccid = params.get("ccid")[0];
                if (ccid == null) {
                    ccid = Util.createPronouncableUID();
                }

                Map<Path, String> submissionFiles = new TreeMap<>();
                Iterator<Map.Entry<String, JsonNode>> it = node.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    String key = entry.getKey();
                    String value = entry.getValue().asText();
                    submissionFiles.put(Paths.get(key), value);
                }

                long startTime = System.nanoTime();
                String report = codeCheck.run("checkNJS", repo, problem, ccid, submissionFiles);
                double elapsed = (System.nanoTime() - startTime) / 1000000000.0;
                if (report == null || report.length() == 0) {
                    report = String.format("Timed out after %5.0f seconds\n", elapsed);
                }

                return Response.ok(report).cookie(new NewCookie("ccid", ccid)).type(MediaType.TEXT_HTML).build();
            } catch (Exception ex) {
                return Response.serverError().entity(Util.getStackTrace(ex)).build();
            }
        });
    }
}

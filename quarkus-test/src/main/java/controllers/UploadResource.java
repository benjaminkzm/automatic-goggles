package com.yourpackage.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.horstmann.codecheck.CodeCheck;
import com.horstmann.codecheck.Util;
import com.horstmann.codecheck.Problem;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.annotations.providers.multipart.PartType;

import models.UploadForm;

@Path("/upload")
public class UploadResource {

    @Inject
    CodeCheck codeCheck;

    @POST
    @Path("/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFiles(@MultipartForm UploadForm form) {
        try {
            String problem = form.getProblem();
            String editKey = Util.createPrivateUID();
            Map<Path, byte[]> problemFiles = new TreeMap<>();

            int n = 1;
            while (form.getFile(n) != null) {
                String filename = form.getFileName(n);
                if (filename != null && !filename.trim().isEmpty()) {
                    String contents = form.getFile(n).replaceAll("\r\n", "\n");
                    problemFiles.put(Path.of(filename), contents.getBytes(StandardCharsets.UTF_8));
                }
                n++;
            }
            problemFiles.put(Path.of("edit.key"), editKey.getBytes(StandardCharsets.UTF_8));
            String response = checkAndSaveProblem(problem, problemFiles);
            return Response.ok(response, MediaType.TEXT_HTML).build();
        } catch (Exception ex) {
            return Response.serverError().entity(Util.getStackTrace(ex)).build();
        }
    }

    private String checkAndSaveProblem(String problem, Map<Path, byte[]> problemFiles)
            throws IOException, InterruptedException, NoSuchMethodException, ScriptException {
        StringBuilder response = new StringBuilder();
        String report = null;
        if (problemFiles.containsKey(Path.of("tracer.js"))) {
            codeCheck.saveProblem("ext", problem, problemFiles);
        } else {
            report = codeCheck.checkAndSave(problem, problemFiles);
        }
        response.append("<html><head><title></title><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>");
        response.append("<body style=\"font-family: sans-serif\">");

        String problemUrl = createProblemUrl(problem, problemFiles);
        response.append("Public URL (for your students): ");
        response.append("<a href=\"" + problemUrl + "\" target=\"_blank\">" + problemUrl + "</a>");
        Path editKeyPath = Path.of("edit.key");
        if (problemFiles.containsKey(editKeyPath)) {
            String editKey = new String(problemFiles.get(editKeyPath), StandardCharsets.UTF_8);
            String editURL = "/private/problem/" + problem + "/" + editKey;
            response.append("<br/>Edit URL (for you only): ");
            response.append("<a href=\"" + editURL + "\">" + editURL + "</a>");
        }
        if (report != null) {
            String run = Base64.getEncoder().encodeToString(report.getBytes(StandardCharsets.UTF_8));
            response.append("<br/><iframe height=\"400\" style=\"width: 90%; margin: 2em;\" src=\"data:text/html;base64," + run + "\"></iframe>");
        }
        response.append("</li>\n");
        response.append("</ul><p></body></html>\n");
        return response.toString();
    }

    private String createProblemUrl(String problem, Map<Path, byte[]> problemFiles) {
        String type;
        if (problemFiles.containsKey(Path.of("tracer.js"))) {
            type = "tracer";
        } else {
            type = "files";
        }
        String problemUrl = "/" + type + "/" + problem;
        return problemUrl;
    }
}

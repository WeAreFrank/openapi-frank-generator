/*
   Copyright 2024 WeAreFrank!
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package nl.wearefrank.openapifrankadapter;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import nl.wearefrank.openapifrankadapter.error.ErrorApiResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import org.xml.sax.SAXException;

import java.io.*;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, SecurityAutoConfiguration.class })
@RestController
public class OpenapiFrankadapterApplication {

    public static void main(String[] args) {

        SpringApplication.run(OpenapiFrankadapterApplication.class, args);

    }

    public static boolean checkUrlContentType(HttpEntity entity) throws IOException {
        String contentType = entity.getContentType().getValue();
        // Check if it's a JSON or YAML url
        return contentType.matches("application/(json|yaml|x-yaml|octet-stream)");
    }

    private static ResponseEntity getFileResponseEntity(MultipartFile file, Option templateOption) throws IOException, SAXException {
        try{
            // Check if it's a JSON or YAML file
            if (!file.getContentType().matches("application/(json|yaml|x-yaml|octet-stream)")) {
                return ResponseEntity.status(415)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new InputStreamResource(new ByteArrayInputStream("{\"message\": \"Unsupported Media Type\"}".getBytes())));
            } else {
                GeneratedFile convertedFile;
                if (!file.getContentType().equals(MediaType.APPLICATION_JSON_VALUE))
                    convertedFile = new GeneratedFile("inputted-api.yaml", file.getBytes());
                else {
                    convertedFile = new GeneratedFile("inputted-api.json", file.getBytes());
                }
                return responseGenerator(convertedFile, templateOption);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InputStreamResource(new ByteArrayInputStream("{\"message\": \"Invalid File\"}".getBytes())));
        }
    }

    private static ResponseEntity getUrlResponseEntity(String url, Option templateOption) throws IOException, SAXException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(url);

            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();

            if (!checkUrlContentType(entity)) {
                EntityUtils.consume(entity);
                return ResponseEntity.status(415)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new InputStreamResource(new ByteArrayInputStream("{\"message\": \"Unsupported Media Type\"}".getBytes())));
            }
            GeneratedFile convertedFile = new GeneratedFile("inputted-api.json", downloadFileFromUrl(entity));
            EntityUtils.consume(entity);
            return responseGenerator(convertedFile, templateOption);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InputStreamResource(new ByteArrayInputStream("{\"message\": \"Invalid URL\"}".getBytes())));
        }
    }

    @PostMapping(value = "/receiver-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> postFileReceiver(@RequestParam("file") MultipartFile file) throws IOException, SAXException {
        return getFileResponseEntity(file, Option.RECEIVER);
    }
    @PostMapping(value = "/receiver-url", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Resource> postUrlReceiver(@RequestParam("url") String url) throws IOException, SAXException {
        return getUrlResponseEntity(url, Option.RECEIVER);
    }

    @PostMapping(value = "/sender-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> postFileSender(@RequestParam("file") MultipartFile file) throws IOException, SAXException {
        return getFileResponseEntity(file, Option.SENDER);
    }

    @PostMapping(value = "/sender-url", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Resource> postUrlSender(@RequestParam("url") String url) throws IOException, SAXException {
        return getUrlResponseEntity(url, Option.SENDER);
    }

    @PostMapping(value = "/xsd-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource>postFileXsd(@RequestParam("file") MultipartFile file) throws IOException, SAXException {
        return getFileResponseEntity(file, Option.XSD);
    }
    
    @PostMapping(value = "/xsd-url", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Resource> postUrlXsd(@RequestParam("url") String url) throws IOException, SAXException {
        return getUrlResponseEntity(url, Option.XSD);
    }

    public static ResponseEntity responseGenerator(GeneratedFile file, Option templateOption) throws IOException, SAXException {

        //// INITIALIZATION ////
        // Generate random folder for which to process the API request
        String uuid = UUID.randomUUID().toString().replaceAll("[^a-zA-Z0-9]", "");

        // Convert the incoming JSON multipart file to String
        String json = new String(file.getContent());

        // Read the openapi specification
        SwaggerParseResult result = new OpenAPIParser().readContents(json, null, null);
        OpenAPI openAPI = result.getOpenAPI();

        // Validate parsed result
        if (openAPI == null) {
            String errorMessage = String.format("Error parsing OpenAPI specification: %s",
                    result.getMessages() != null ? String.join(", ", result.getMessages()) : "No additional details.");
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InputStreamResource(new ByteArrayInputStream(String.format("{\"message\": \"%s\"}", errorMessage).getBytes())));
        }

        List<GeneratedFile> files;
        // Try catch for error handling return
        try {
            files = XMLGenerator.execute(openAPI, templateOption);
        } catch (ErrorApiResponse error) {
            return ResponseEntity.status(error.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InputStreamResource(new ByteArrayInputStream(error.getMessage().getBytes())));
        }

        // Generate the Configuration.xml file
        GeneratedFile configXmlFile = generateConfigurationXml(files);
        files.add(configXmlFile);

        // Generate the zip file; add original file to the zip file
        files.add(file);
        byte[] response = convertToZip(files);

        // Return the zip file as a resource
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + templateOption + "-" + uuid + ".zip\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(new ByteArrayInputStream(response)));
    }

    private static GeneratedFile generateConfigurationXml(List<GeneratedFile> files) {
        StringBuilder xmlContent = new StringBuilder();
        xmlContent.append("<Configuration>\n");

        for (GeneratedFile file : files) {
            String fileName = file.getName();
            if (fileName.endsWith(".xml")) {
                String dirName = fileName.substring(0, fileName.lastIndexOf('.'));
                xmlContent.append(" <Include ref=\"").append(dirName).append("/" + "xml" + "/").append(fileName).append("\"/>\n");
            }
        }

        xmlContent.append("</Configuration>");

        return new GeneratedFile("Configuration.xml", xmlContent.toString().getBytes());
    }

    //// Method to convert in-memory files into a singular zip file ////
    public static byte[] convertToZip(List<GeneratedFile> files) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);

        for (GeneratedFile file : files) {
            String fileName = file.getName();
            if (fileName.startsWith("inputted-api") || fileName.startsWith("Configuration")) {
                // Add inputted-api file directly to the root of the zip
                ZipEntry entry = new ZipEntry(fileName);
                zipOutputStream.putNextEntry(entry);
                zipOutputStream.write(file.getContent());
                zipOutputStream.closeEntry();
            } else {
                // Create subdirectories for each adapter
                String adapterName = fileName.substring(0, fileName.lastIndexOf('.'));
                String subDir = fileName.endsWith(".xml") ? "xml" : "xsd";
                String entryName = adapterName + "/" + subDir + "/" + fileName;

                ZipEntry entry = new ZipEntry(entryName);
                zipOutputStream.putNextEntry(entry);
                zipOutputStream.write(file.getContent());
                zipOutputStream.closeEntry();
            }
        }

        zipOutputStream.close();
        byteArrayOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }
    //// Method to download a file from a URL ////
    public static byte[] downloadFileFromUrl(HttpEntity entity) throws IOException {

        if (entity != null) {
            try (InputStream inputStream = entity.getContent();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }


                return outputStream.toByteArray();
            }
        } else {
            throw new IOException("Empty or null response received.");
        }
    }
}

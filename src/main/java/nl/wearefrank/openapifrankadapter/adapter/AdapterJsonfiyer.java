package nl.wearefrank.openapifrankadapter.adapter;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import org.json.simple.JSONObject;

import java.util.Map;

public class AdapterJsonfiyer {
    Map.Entry<String, PathItem> path;
    OpenAPI openAPI;
    String fileName;
    AdapterRefs adapterRefs;

    public AdapterJsonfiyer(OpenAPI openAPI, Map.Entry<String, PathItem> path, AdapterRefs adapterRefs) {
        this.path = path;
        this.openAPI = openAPI;
        this.fileName = path.getKey().substring(1).replace("/", "-") + "_Configuration.json";
        this.adapterRefs = adapterRefs;
    }

    // Convert string to JSON
    public JSONObject getAdapterJsonObj() {
        // First make the new JSONObject
        JSONObject adapterJson = new JSONObject();
        // Add the name
        adapterJson.put("name", new AdapterClass(this.openAPI, this.path).getAdapterName());
        // Add the description
        adapterJson.put("description", new AdapterClass(this.openAPI, this.path).getAdapterDescription());
        // Add the type
        adapterJson.put("type", "adapter");
        // Add the receiver
        JSONObject receiverJson = new JSONObject();
        receiverJson.put("name", new ReceiverClass(this.path).getReceiverName());
        adapterJson.put("receiver", receiverJson);
        // Add the apiListener
        JSONObject apiListenerJson = new JSONObject();
        apiListenerJson.put("name", new ApiListenerClass(this.path).getApiListenerName());
        apiListenerJson.put("method", new ApiListenerClass(this.path).getMethod());
        apiListenerJson.put("uriPattern", new ApiListenerClass(this.path).getUriPattern());
        apiListenerJson.put("produces", new ApiListenerClass(this.path).getProduces());
        adapterJson.put("apiListener", apiListenerJson);
        // Add the adapterRefs
        JSONObject adapterRefsJson = new JSONObject();
        adapterRefsJson.put("schema", adapterRefs.schemaLocation);
        adapterRefsJson.put("root", adapterRefs.root);
        adapterRefsJson.put("responseRoot", adapterRefs.responseRoot);
        adapterJson.put("adapterRefs", adapterRefsJson);

        // instantiate paramSingleton
        String[] params = adapterRefs.parameters.toArray(new String[0]);
        // add the params as a JSONObject
        adapterJson.put("parameters", params);

        // Return the JSONObject
        return adapterJson;
    }
}
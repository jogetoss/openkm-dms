package org.joget.marketplace.util;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.joget.commons.util.LogUtil;
import org.joget.marketplace.model.ApiResponse;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DMSOpenKMUtil {
    
    public String getClassName() {
        return getClass().getName();
    }

    /**
     * Utility method to log API errors in a consistent way.
     * Prints out the response code and message (response body).
     */
    public static void logApiError(String context, ApiResponse apiResponse) {
        if (apiResponse == null) {
            LogUtil.error(DMSOpenKMUtil.class.getName(), null, context + " - ApiResponse is null");
            return;
        }

        String body = apiResponse.getResponseBody();
        String message = (body != null && !body.isEmpty()) ? body : "No response body";
        LogUtil.error(
            DMSOpenKMUtil.class.getName(),
            null,
            context + " - Error Code=" + apiResponse.getResponseCode() + ", Message=" + message
        );
    }

    // file upload usage
    public ApiResponse authApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort) {
        ApiResponse apiResponse = null;
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpGet getRequest = new HttpGet(endPoint);
        getRequest.addHeader("Accept", "application/xml");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);
        
        CloseableHttpClient httpclient = clientbuilder.build();

        try {
            apiResponse = new ApiResponse();      
            CloseableHttpResponse httpresponse = httpclient.execute(getRequest);
            apiResponse.setResponseCode(httpresponse.getStatusLine().getStatusCode());
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error calling authApi: " + ex.getMessage());
        }
        return apiResponse;
    }

    public ApiResponse createFolderApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort, String folderName, String openkmFileUploadPath) {
        ApiResponse apiResponse = null;
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpPost postRequest = new HttpPost(endPoint);
        postRequest.addHeader("Accept", "application/json");
        postRequest.addHeader("Content-Type", "application/json");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);
        
        CloseableHttpClient httpclient = clientbuilder.build();

        try {
            apiResponse = new ApiResponse();
            
            StringEntity requestEntity = new StringEntity(openkmFileUploadPath + "/" + folderName, ContentType.APPLICATION_JSON);
            postRequest.setEntity(requestEntity);
            
            CloseableHttpResponse httpresponse = httpclient.execute(postRequest);
            apiResponse.setResponseCode(httpresponse.getStatusLine().getStatusCode());
            apiResponse.setResponseBody(EntityUtils.toString(httpresponse.getEntity()));
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error calling createFolderApi: " + ex.getMessage());
            if (apiResponse == null) {
                apiResponse = new ApiResponse();
            }
            apiResponse.setResponseCode(500);
            apiResponse.setResponseBody(ex.getMessage());
        }
        return apiResponse;
    }

    public String createFileApi(String endPoint, String username, String password, String openkmURLHost, Integer openkmURLPort, String fileName, File file, String folderName, String openkmFileUploadPath) {
        String documentId = "";
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("docPath", openkmFileUploadPath + "/" + folderName + "/" + fileName)
                .addFormDataPart("content", fileName,
                        RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .build();
        Request request = new Request.Builder()
                .url(endPoint)
                .method("POST", body)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Basic " + encodedAuth)
                .build();
        try (Response response = client.newCall(request).execute()) {
            // Handle the response
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject jSONObject = new JSONObject(responseBody);
                documentId = jSONObject.get("uuid").toString();
            } else {
                LogUtil.error(
                    getClass().getName(),
                    null,
                    "Error calling createFileApi - Error Code=" + response.code() + ", Message=" + response.message()
                );
                return null;
            }
        } catch (IOException e) {
            LogUtil.error(getClass().getName(), e, "IOException in createFileApi: " + e.getMessage());
        }
        return documentId;
    }

    public String updateFileAfterCheckoutApi(String endPoint, String username, String password, String openkmURLHost, Integer openkmURLPort, String fileName, File file, String documentId) {
        String version = "";
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("docId", documentId)
                .addFormDataPart("content", fileName, RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .build();
        Request request = new Request.Builder()
                .url(endPoint)
                .method("POST", body)
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Basic " + encodedAuth)
                .build();
        try (Response response = client.newCall(request).execute()) {
            // Handle the response
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject jSONObject = new JSONObject(responseBody);
                version = jSONObject.get("name").toString();
            } else {
                LogUtil.error(
                    getClass().getName(),
                    null,
                    "Error calling updateFileAfterCheckoutApi - Error Code=" + response.code() + ", Message=" + response.message()
                );
                return null;
            }
        } catch (IOException e) {
            LogUtil.error(getClass().getName(), e, "IOException in updateFileAfterCheckoutApi: " + e.getMessage());
        }
        return version;
    }

    public ApiResponse deleteApi(String endPoint, String username, String password, String openkmURLHost, Integer openkmURLPort) {
        ApiResponse apiResponse = null;
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpDelete deleteRequest = new HttpDelete(endPoint);
        deleteRequest.addHeader("Accept", "application/json");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);

        CloseableHttpClient httpclient = clientbuilder.build();
        
        try {
            apiResponse = new ApiResponse();
            CloseableHttpResponse httpresponse = httpclient.execute(deleteRequest);
            apiResponse.setResponseCode(httpresponse.getStatusLine().getStatusCode());
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error calling deleteApi: " + ex.getMessage());
        }
        return apiResponse; 
    }

    public ApiResponse getApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort) {
        ApiResponse apiResponse = null;
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpGet getRequest = new HttpGet(endPoint);
        getRequest.addHeader("Accept", "application/json");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);
        
        CloseableHttpClient httpclient = clientbuilder.build();

        try {
            apiResponse = new ApiResponse();      
            CloseableHttpResponse httpresponse = httpclient.execute(getRequest);
            apiResponse.setResponseCode(httpresponse.getStatusLine().getStatusCode());
            if (httpresponse.getEntity()!=null) {
                apiResponse.setResponseBody(EntityUtils.toString(httpresponse.getEntity()));
            }
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error calling getApi: " + ex.getMessage());
        }
        return apiResponse;
    }

    public ApiResponse putApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort) {
        ApiResponse apiResponse = null;
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpPut putRequest = new HttpPut(endPoint);
        putRequest.addHeader("Accept", "application/json");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);
        
        CloseableHttpClient httpclient = clientbuilder.build();

        try {
            apiResponse = new ApiResponse();      
            CloseableHttpResponse httpresponse = httpclient.execute(putRequest);
            apiResponse.setResponseCode(httpresponse.getStatusLine().getStatusCode());
            if (httpresponse.getEntity()!=null) {
                apiResponse.setResponseBody(EntityUtils.toString(httpresponse.getEntity()));
            }
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, "Error calling putApi: " + ex.getMessage());
        }
        return apiResponse;
    }
}

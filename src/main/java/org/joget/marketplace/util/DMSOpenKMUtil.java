package org.joget.marketplace.util;

import java.io.File;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.joget.commons.util.LogUtil;
import org.joget.marketplace.model.ApiResponse;
import org.json.JSONObject;

public class DMSOpenKMUtil {
    
    public String getClassName() {
        return getClass().getName();
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
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
        return apiResponse;
    }

    public void createFolderApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort, String folderName, String openkmFileUploadPath) {
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
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
    }

    public String createFileApi(String endPoint, String username, String password, String openkmURLHost, Integer openkmURLPort, String fileName, File file, String folderName, String openkmFileUploadPath) {
        String documentId = "";
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpPost postRequest = new HttpPost(endPoint);
        postRequest.addHeader("Accept", "application/json");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);

        CloseableHttpClient httpclient = clientbuilder.build();

        try {
            HttpEntity requestEntity = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addBinaryBody("content", file, ContentType.DEFAULT_BINARY, fileName)
            .addTextBody("docPath", openkmFileUploadPath + "/" + folderName + "/" + fileName)
            .build();
            postRequest.setEntity(requestEntity);
            
            CloseableHttpResponse httpresponse = httpclient.execute(postRequest);
            int statusCode = httpresponse.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                String responseBody = EntityUtils.toString(httpresponse.getEntity());
                JSONObject jSONObject = new JSONObject(responseBody);
                documentId = jSONObject.get("uuid").toString();
            } else {
                LogUtil.info(getClassName(), "HTTP Request failed with status code: " + statusCode);
            }
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
        return documentId; 
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
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
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
            apiResponse.setResponseBody(EntityUtils.toString(httpresponse.getEntity()));
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
        return apiResponse;
    }
}

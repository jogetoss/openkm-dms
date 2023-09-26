package org.joget.marketplace;

import java.net.URL;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormBinder;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormLoadOptionsBinder;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.marketplace.model.ApiResponse;
import org.json.JSONArray;
import org.json.JSONObject;

public class DMSOpenKMFormOptionsBinder extends FormBinder implements FormLoadOptionsBinder {

    private final static String MESSAGE_PATH = "messages/DMSOpenKMFormOptionsBinder";
    
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmformoptionsbinder.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getVersion() {
        return "8.0.0";
    }

    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmformoptionsbinder.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmformoptionsbinder.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getClassName() {
        return getClass().getName();
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DMSOpenKMFormOptionsBinder.json", null, true, MESSAGE_PATH);
    }
    
    public FormRowSet load(Element element, String primaryKey, FormData formData) {
        FormRowSet results = new FormRowSet();
        results.setMultiRow(true);

        String username = getPropertyString("username");
        String password = getPropertyString("password");
        String openkmURL = getPropertyString("openkmURL");
        String folderRootID = getPropertyString("folderRootID");
        String openkmURLHost = "";
        Integer openkmURLPort = 0;

        try {
            URL url = new URL(openkmURL);
            openkmURLHost = url.getHost(); 
            openkmURLPort = url.getPort(); 

        } catch (Exception e) {
            LogUtil.error(this.getClassName(), e, e.getMessage());
        }

        ApiResponse getRootFolderApiResponse = getRootFolderApi(openkmURL + "/services/rest/folder/getPath/" + folderRootID, username, password, openkmURLHost, openkmURLPort);
        if (getRootFolderApiResponse != null && getRootFolderApiResponse.getResponseCode() == 200) {
            String rootPath = getRootFolderApiResponse.getResponseBody();
            FormRow emptyRow = new FormRow();
            emptyRow.setProperty(FormUtil.PROPERTY_VALUE, rootPath);
            emptyRow.setProperty(FormUtil.PROPERTY_LABEL, rootPath);
            emptyRow.setProperty(FormUtil.PROPERTY_GROUPING, "");
            results.add(emptyRow);
        }
       
        results = getChildrenFolders(results, openkmURL, folderRootID, username, password, openkmURLHost, openkmURLPort);

        return results;
    }

    protected FormRowSet getChildrenFolders(FormRowSet results, String openkmURL, String folderRootID, String username, String password, String openkmURLHost,Integer openkmURLPort) {
        ApiResponse getChildrenFoldersApiResponse = getChildrenFoldersApi(openkmURL + "/services/rest/folder/getChildren?fldId=" + folderRootID, username, password, openkmURLHost, openkmURLPort);
        JSONObject jsonObjectFolders = new JSONObject(getChildrenFoldersApiResponse.getResponseBody());
        
        if (jsonObjectFolders.length() != 0) {
            Object folderData = jsonObjectFolders.get("folder");

            if (folderData instanceof JSONObject) {
                JSONObject folderObject = (JSONObject) folderData;
                getFolderObject(folderObject, results, openkmURL, folderRootID, username, password, openkmURLHost, openkmURLPort);

            } else if (folderData instanceof JSONArray) {
                JSONArray multipleFolders = (JSONArray) folderData;
                for (int i = 0; i < multipleFolders.length(); i++) {
                    JSONObject folderObject = multipleFolders.getJSONObject(i);
                    getFolderObject(folderObject, results, openkmURL, folderRootID, username, password, openkmURLHost, openkmURLPort);
                  
                }
            }
        }
        return results;
    }

    protected void getFolderObject(JSONObject folderObject, FormRowSet results, String openkmURL, String folderRootID, String username, String password, String openkmURLHost,Integer openkmURLPort) {
        String path = folderObject.getString("path");
        if (path != null && !path.equals("")) {
            FormRow emptyRow = new FormRow();
            emptyRow.setProperty(FormUtil.PROPERTY_VALUE, path);
            emptyRow.setProperty(FormUtil.PROPERTY_LABEL, path);
            emptyRow.setProperty(FormUtil.PROPERTY_GROUPING, "");
            results.add(emptyRow);

            String childrenFolderID = folderObject.getString("uuid");
            getChildrenFolders(results, openkmURL, childrenFolderID, username, password, openkmURLHost, openkmURLPort);
        }
    }

    protected ApiResponse getRootFolderApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort) {
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
            apiResponse.setResponseBody(EntityUtils.toString(httpresponse.getEntity()));
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
        return apiResponse;
    }

    protected ApiResponse getChildrenFoldersApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort) {
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
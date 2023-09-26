package org.joget.marketplace;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
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
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.model.FormStoreBinder;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.marketplace.model.ApiResponse;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

public class DMSOpenKMFileUpload extends FileUpload {
    private final static String MESSAGE_PATH = "messages/DMSOpenKMFileUpload";
    FormStoreBinder storeBinder;

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmfileupload.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getVersion() {
        return "8.0.0";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmfileupload.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getFormBuilderCategory() {
        return "Marketplace";
    }

    @Override
    public String getLabel() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmfileupload.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DMSOpenKMFileUpload.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String renderTemplate(FormData formData, Map dataModel) {
        String template = "fileUpload.ftl";

        String username = getPropertyString("username");
        String password = getPropertyString("password");
        String openkmURL = getPropertyString("openkmURL");
        String openkmFileUploadPathField = getPropertyString("openkmFileUploadPath");
        String createFolderFormID = getPropertyString("createFolderFormID");
        String openkmURLHost = "";
        Integer openkmURLPort = 0;
        String protocol = "";
        String hostAndPort = "";
        JSONObject jsonParams = new JSONObject();

        try {
            URL url = new URL(openkmURL);
            openkmURLHost = url.getHost(); 
            openkmURLPort = url.getPort(); 
            protocol = url.getProtocol(); 
            hostAndPort = url.getHost() + (url.getPort() != -1 ? (":" + url.getPort()) : "");
        } catch (Exception e) {
            LogUtil.error(this.getClassName(), e, e.getMessage());
        }
        
        if (Boolean.parseBoolean(dataModel.get("includeMetaData").toString())) {
            try {
                //check if openkm authentication is true
                ApiResponse authApiResponse = authApi(openkmURL + "/services/rest/auth/login", username, password, openkmURLHost, openkmURLPort);
                if (authApiResponse != null && authApiResponse.getResponseCode() != 204) {
                    dataModel.put("error", "Authentication ERROR");
                }
            } catch (Exception e) {
                dataModel.put("error", e.getLocalizedMessage());
            }
        }
      
        // set value
        String[] values = FormUtil.getElementPropertyValues(this, formData);
        
        Map<String, String> tempFilePaths = new HashMap<String, String>();
        Map<String, String> filePaths = new HashMap<String, String>();
                
        String primaryKeyValue = getPrimaryKeyValue(formData);
        String formDefId = "";
        Form form = FormUtil.findRootForm(this);
        if (form != null) {
            formDefId = form.getPropertyString(FormUtil.PROPERTY_ID);
        }
        String appId = "";
        String appVersion = "";

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        if (appDef != null) {
            appId = appDef.getId();
            appVersion = appDef.getVersion().toString();
        }

        for (String value : values) {
            // check if the file is in temp file
            File file = FileManager.getFileByPath(value);
            
            if (file != null) {
                tempFilePaths.put(value, file.getName());
            } else if (value != null && !value.isEmpty()) {
                // determine actual path for the file uploads
                String fileName = value;
                String encodedFileName = fileName;
                if (fileName != null) {
                    try {
                        encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
                    } catch (UnsupportedEncodingException ex) {
                        // ignore
                    }
                }
      
                // get openKM file path value
                ApplicationContext ac = AppUtil.getApplicationContext();
                AppService appService = (AppService) ac.getBean("appService");
                String openkmFileUploadPath = "";
                FormRow row = new FormRow();
                FormRowSet rowSet = appService.loadFormData(appId, appVersion, formDefId, primaryKeyValue);
                if (rowSet != null && !rowSet.isEmpty()) {
                    row = rowSet.get(0);
                    openkmFileUploadPath = row.getProperty(openkmFileUploadPathField);
                    if (openkmFileUploadPath == null) {
                        if(createFolderFormID.equals("true")){
                            openkmFileUploadPath = "/okm:root/" + primaryKeyValue;
                        } else {
                            openkmFileUploadPath = "/okm:root";
                        }
                    }
                }
                
                jsonParams.put("protocol", protocol);
                jsonParams.put("username", username);
                jsonParams.put("password", password);
                jsonParams.put("hostAndPort", hostAndPort);
                jsonParams.put("openkmFileUploadPath", openkmFileUploadPath);
                jsonParams.put("fileName", fileName);
                String params = StringUtil.escapeString(SecurityUtil.encrypt(jsonParams.toString()), StringUtil.TYPE_URL, null);

                String filePath = "/web/json/app/" + appId + "/" + appVersion + "/plugin/org.joget.marketplace.DMSOpenKMFileUpload/service?action=download&params=" + params;
                filePaths.put(filePath, value);
            }
        }
        
        if (!tempFilePaths.isEmpty()) {
            dataModel.put("tempFilePaths", tempFilePaths);
        }
        if (!filePaths.isEmpty()) {
            dataModel.put("filePaths", filePaths);
        }
        
        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        
        if (Boolean.parseBoolean(dataModel.get("includeMetaData").toString())) {
            html = html.replace("<div class=\"form-fileupload\">", "<span class=\"form-floating-label\">OpenKM</span><div class=\"form-fileupload\">");
        }
        
        return html;
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        String username = getPropertyString("username");
        String password = getPropertyString("password");
        String openkmURL = getPropertyString("openkmURL");
        String openkmFileUploadPathField = getPropertyString("openkmFileUploadPath");
        String openkmFileUploadPath = formData.getRequestParameter(getPropertyString("openkmFileUploadPath"));
        // default path
        if (openkmFileUploadPath == null) {
            openkmFileUploadPath = "/okm:root";
        }
        String createFolderFormID = getPropertyString("createFolderFormID");
        String openkmURLHost = "";
        Integer openkmURLPort = 0;
        String folderName = "";

        try {
            URL url = new URL(openkmURL);
            openkmURLHost = url.getHost(); 
            openkmURLPort = url.getPort(); 

        } catch (Exception e) {
            LogUtil.error(this.getClassName(), e, e.getMessage());
        }
        
        FormRowSet rowSet = null;

        String id = getPropertyString(FormUtil.PROPERTY_ID);
        
        Set<String> remove = null;
        if ("true".equals(getPropertyString("removeFile"))) {
            remove = new HashSet<String>();
            Form form = FormUtil.findRootForm(this);
            String originalValues = formData.getLoadBinderDataProperty(form, id);
            if (originalValues != null) {
                remove.addAll(Arrays.asList(originalValues.split(";")));
            }
        }

        // get value
        if (id != null) {
            String[] values = FormUtil.getElementPropertyValues(this, formData);
            if (values != null && values.length > 0) {
                // set value into Properties and FormRowSet object
                FormRow result = new FormRow();
                List<String> resultedValue = new ArrayList<String>();
                List<String> filePaths = new ArrayList<String>();
                
                for (String value : values) {
                    // check if the file is in temp file
                    File file = FileManager.getFileByPath(value);
                    if (file != null) {
                        filePaths.add(value);
                        resultedValue.add(file.getName());
                        
                        // write file to openKM
                        if(createFolderFormID.equals("true")){
                            folderName = createFolderApi(openkmURL + "/services/rest/folder/createSimple",  username, password, openkmURLHost, openkmURLPort, formData.getPrimaryKeyValue(), openkmFileUploadPath);
                        }
                        ApiResponse createFileApiResponse = createFileApi(openkmURL + "/services/rest/document/createSimple", username, password, openkmURLHost, openkmURLPort, file.getName(), file, folderName, openkmFileUploadPath);    
                        if (createFileApiResponse != null && createFileApiResponse.getResponseCode() != 200) {
                            LogUtil.info(getClassName(), createFileApiResponse.getResponseBody());
                        }
                    } else {
                        if (remove != null && !value.isEmpty()) {
                            remove.remove(value);
                        }
                        resultedValue.add(value);

                    }
                }
                
                // if (!filePaths.isEmpty()) {
                //     result.putTempFilePath(id, filePaths.toArray(new String[]{}));
                // }
                
                if (remove != null) {
                    result.putDeleteFilePath(id, remove.toArray(new String[]{}));
                }
                
                // formulate values
                String delimitedValue = FormUtil.generateElementPropertyValues(resultedValue.toArray(new String[]{}));
                String paramName = FormUtil.getElementParameterName(this);
                formData.addRequestParameterValues(paramName, resultedValue.toArray(new String[]{}));
                        
                // set value into Properties and FormRowSet object
                result.setProperty(id, delimitedValue);
                 // modify path field
                 if(createFolderFormID.equals("true") && !openkmFileUploadPathField.equals("")){
                    result.setProperty(openkmFileUploadPathField, openkmFileUploadPath + "/" + folderName);                
                }
                rowSet = new FormRowSet();
                rowSet.add(result);
                
                String filePathPostfix = "_path";
                formData.addRequestParameterValues(id + filePathPostfix, new String[]{});
            }
        }
        
        return rowSet;
    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        if ("download".equals(action)) {
            String params = SecurityUtil.decrypt(request.getParameter("params"));
            JSONObject jsonParams = new JSONObject(params);

            String protocol = jsonParams.getString("protocol");
            String username = jsonParams.getString("username");
            String password = jsonParams.getString("password");
            String hostAndPort = jsonParams.getString("hostAndPort");
            String openkmFileUploadPath = jsonParams.getString("openkmFileUploadPath");
            String fileName = jsonParams.getString("fileName");

            String filePath = protocol + "://" + username + ":" + password + "@" + hostAndPort + "/OpenKM/Download?path=" + openkmFileUploadPath + "/" + fileName;

            response.sendRedirect(filePath);
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    protected ApiResponse authApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort) {
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

    protected String createFolderApi(String endPoint, String username, String password, String openkmURLHost,Integer openkmURLPort, String folderName, String openkmFileUploadPath) {
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
        return folderName; 
    }

    protected ApiResponse createFileApi(String endPoint, String username, String password, String openkmURLHost, Integer openkmURLPort, String fileName, File file, String folderName, String openkmFileUploadPath) {
        ApiResponse apiResponse = null;
        HttpClientBuilder clientbuilder = HttpClients.custom();
        HttpPost postRequest = new HttpPost(endPoint);
        postRequest.addHeader("Accept", "application/json");
        //postRequest.addHeader("Content-Type", "multipart/form-data;charset=UTF-8");

        CredentialsProvider credentialsPovider = new BasicCredentialsProvider();
        credentialsPovider.setCredentials(new AuthScope(openkmURLHost, openkmURLPort), 
        new UsernamePasswordCredentials(username, password));
        clientbuilder = clientbuilder.setDefaultCredentialsProvider(credentialsPovider);

        CloseableHttpClient httpclient = clientbuilder.build();

        try {
            apiResponse = new ApiResponse();

            HttpEntity requestEntity = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
            .addBinaryBody("content", file, ContentType.DEFAULT_BINARY, fileName)
            .addTextBody("docPath", openkmFileUploadPath + "/" + folderName + "/" + fileName)
            .build();
            postRequest.setEntity(requestEntity);
            
            CloseableHttpResponse httpresponse = httpclient.execute(postRequest);
            apiResponse.setResponseCode(httpresponse.getStatusLine().getStatusCode());
            apiResponse.setResponseBody(EntityUtils.toString(httpresponse.getEntity()));
        } catch (Exception ex) {
            LogUtil.error(getClass().getName(), ex, ex.getMessage());
        }
        return apiResponse; 
    }
}
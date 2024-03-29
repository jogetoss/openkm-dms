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
import org.joget.marketplace.util.DMSOpenKMUtil;
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
        return "8.0.1";
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
        DMSOpenKMUtil openkmUtil = new DMSOpenKMUtil();
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
                ApiResponse authApiResponse = openkmUtil.authApi(openkmURL + "/services/rest/auth/login", username, password, openkmURLHost, openkmURLPort);
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

        if(values.length != 0){
            if(!values[0].isEmpty()){
                for (String value : values) {
                    // check if the file is in temp file
                    Map<String, String> fileMap = parseFileName(value);
                    value = fileMap.get("filename");
                    String documentId = fileMap.get("documentId");
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
    public FormData formatDataForValidation(FormData formData) {
        String filePathPostfix = "_path";
        String id = FormUtil.getElementParameterName(this);
        if (id != null) {
            String[] tempFilenames = formData.getRequestParameterValues(id);
            String[] tempExisting = formData.getRequestParameterValues(id + filePathPostfix);
            
            List<String> filenames = new ArrayList<String>();
            if (tempFilenames != null && tempFilenames.length > 0) {
                filenames.addAll(Arrays.asList(tempFilenames));
            }

            if (tempExisting != null && tempExisting.length > 0) {
                filenames.addAll(Arrays.asList(tempExisting));
            }

            if (filenames.isEmpty()) {
                formData.addRequestParameterValues(id, new String[]{""});
            } else if (!"true".equals(getPropertyString("multiple"))) {
                formData.addRequestParameterValues(id, new String[]{filenames.get(0)});
            } else {
                formData.addRequestParameterValues(id, filenames.toArray(new String[]{}));
            }
        }
        return formData;
    }

    @Override
    public FormRowSet formatData(FormData formData) {
        DMSOpenKMUtil openkmUtil = new DMSOpenKMUtil();
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
        String documentId = "";

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
        Form form = new Form();
        if ("true".equals(getPropertyString("removeFile"))) {
            remove = new HashSet<String>();
            form = FormUtil.findRootForm(this);
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
                folderName =  formData.getPrimaryKeyValue();

                for (String value : values) {
                    // check if the file is in temp file
                    File file = FileManager.getFileByPath(value);
                    if (file != null) {
                        // write file to openKM
                        if(createFolderFormID.equals("true")){
                            if (!openkmFileUploadPath.contains(folderName)) {
                                openkmUtil.createFolderApi(openkmURL + "/services/rest/folder/createSimple",  username, password, openkmURLHost, openkmURLPort, folderName, openkmFileUploadPath);
                            } else {
                                folderName = "";
                            }
                        } else {
                            folderName = "";
                        }
                        documentId = openkmUtil.createFileApi(openkmURL + "/services/rest/document/createSimple", username, password, openkmURLHost, openkmURLPort, file.getName(), file, folderName, openkmFileUploadPath);    

                        filePaths.add(value + "|" + documentId);
                        resultedValue.add(file.getName() + "|" + documentId);
                    } else {
                        if (remove != null && !value.isEmpty()) {
                            // remove.remove(value);
                            remove.removeIf(item -> {
                                    if (item.contains(value)) {
                                        resultedValue.add(item);
                                        return true;
                                    }
                                return false;
                            });
                        } else {
                             folderName = "";
                        }
                    }
                }
                
                if (!filePaths.isEmpty()) {
                    result.putTempFilePath(id, filePaths.toArray(new String[]{}));
                }
                
                 if (remove != null && !remove.isEmpty() && !remove.contains("")) {
                    result.putDeleteFilePath(id, remove.toArray(new String[]{}));
                    for (String r : remove) {
                        Map<String, String> fileMap = parseFileName(r);
                        documentId = fileMap.get("documentId");
                    
                        // delete file(s) from openkm
                        ApiResponse deleteFileApiResponse = openkmUtil.deleteApi(openkmURL + "/services/rest/document/delete?docId=" + documentId, username, password, openkmURLHost, openkmURLPort);    
                        if (deleteFileApiResponse != null && deleteFileApiResponse.getResponseCode() != 204) {
                            LogUtil.info(getClassName(), deleteFileApiResponse.getResponseBody());
                        }

                        // delete folder that contains the file above
                        if ("true".equals(getPropertyString("removeFolder"))) {
                            ApiResponse getFolderIdApiResponse = openkmUtil.getApi(openkmURL + "/services/rest/repository/getNodeUuid?nodePath=" + openkmFileUploadPath, username, password, openkmURLHost, openkmURLPort);
                            if (getFolderIdApiResponse != null && getFolderIdApiResponse.getResponseCode() == 200) {
                                String folderId = getFolderIdApiResponse.getResponseBody();

                                ApiResponse deleteFolderApiResponse = openkmUtil.deleteApi(openkmURL + "/services/rest/folder/delete?fldId=" + folderId, username, password, openkmURLHost, openkmURLPort);    
                                if (deleteFolderApiResponse != null && deleteFolderApiResponse.getResponseCode() != 204) {
                                    LogUtil.info(getClassName(), deleteFolderApiResponse.getResponseBody());
                                } else {
                                    folderName = "";
                                }
                            } else {
                                LogUtil.info(getClassName(), getFolderIdApiResponse.getResponseBody());
                            }
                        }
                    }
                }
                
                // formulate values
                String delimitedValue = FormUtil.generateElementPropertyValues(resultedValue.toArray(new String[] {}));
                String paramName = FormUtil.getElementParameterName(this);
                formData.addRequestParameterValues(paramName, resultedValue.toArray(new String[] {}));

                // modify path field
                if (createFolderFormID.equals("true") && !openkmFileUploadPathField.equals("")) {
                    if (!openkmFileUploadPath.contains(folderName)) {
                        result.setProperty(openkmFileUploadPathField, openkmFileUploadPath + "/" + folderName);
                    } else {
                        result.setProperty(openkmFileUploadPathField, openkmFileUploadPath);
                    }
                }

                // set value into Properties and FormRowSet object
                if (delimitedValue == null) {
                    delimitedValue = "";
                    result.setProperty(openkmFileUploadPathField, "");
                }
                result.setProperty(id, delimitedValue);

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

    public Map<String, String> parseFileName(String input) {
        Map<String, String> resultMap = new HashMap<>();

        // Split the input based on "|"
        String[] parts = input.split("\\|");

        if (parts.length == 2) {
            // Extract the filename (part before "|")
            String filename = parts[0].trim();
            String documentId = parts[1].trim();

            resultMap.put("filename", filename);
            resultMap.put("documentId", documentId);
        } else {
            LogUtil.info(getClassName(), "Invalid input format.");
        }

        return resultMap;
    }
}
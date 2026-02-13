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
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.marketplace.model.ApiResponse;
import org.joget.marketplace.util.DMSOpenKMUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;

public class DMSOpenKMFileUpload extends FileUpload {
    private final static String MESSAGE_PATH = "messages/DMSOpenKMFileUpload";

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
        return Activator.VERSION;
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
        if (openkmURL.endsWith("/")) {
            openkmURL = openkmURL.substring(0, openkmURL.length() - 1);
        }
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
            LogUtil.error(this.getClassName(), e, "Error parsing OpenKM URL in renderTemplate: " + e.getMessage());
        }
        
        if (Boolean.parseBoolean(dataModel.get("includeMetaData").toString())) {
            try {
                //check if openkm authentication is true
                ApiResponse authApiResponse = openkmUtil.authApi(openkmURL + "/services/rest/auth/login", username, password, openkmURLHost, openkmURLPort);
                if (authApiResponse != null && authApiResponse.getResponseCode() != 204) {
                    dataModel.put("error", "Authentication ERROR");
                    DMSOpenKMUtil.logApiError("OpenKM authentication failed", authApiResponse);
                }
            } catch (Exception e) {
                LogUtil.error(this.getClassName(), e, "Error during OpenKM authentication: " + e.getMessage());
                dataModel.put("error", e.getLocalizedMessage());
            }
        }
      
        // set value
        String[] values = FormUtil.getElementPropertyValues(this, formData);
        
        Map<String, String> tempFilePaths = new HashMap<String, String>();
        Map<String, String> filePaths = new HashMap<String, String>();
                
        String primaryKeyValue = getPrimaryKeyValue(formData);
        String formDefId = "";

        String filePathPostfix = "_path";
        String id = FormUtil.getElementParameterName(this);

        // check is there a stored value
        Boolean fromTemp = false;
        String storedValue = formData.getStoreBinderDataProperty(this);
        if (storedValue != null) {
            values = storedValue.split(";");
        } else {
            // if there is no stored value, get the temp files
            String[] tempExisting = formData.getRequestParameterValues(id + filePathPostfix);

            if (tempExisting != null && tempExisting.length > 0) {
                values = tempExisting;
            }
        }

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
                    if(fileMap.get("filename") != null){
                        value = fileMap.get("filename");
                        String documentId = fileMap.get("documentId");
                    }                    
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

            // bind id to file names
            String[] fileWithIds = FormUtil.getElementPropertyValues(this, formData);

            if (tempExisting != null && tempExisting.length > 0) {
                for (int i = 0; i < tempExisting.length; i++) {
                    for (int j = 0; j < fileWithIds.length; j++) {
                        String[] parts = fileWithIds[j].split("\\|");
                        if (parts.length == 2) {
                            String filename = parts[0];
                            String docid = parts[1];
        
                            if (tempExisting[i].equals(filename)) {
                                tempExisting[i] = tempExisting[i] + "|" + docid;
                            }
                        }
                    }
                }
            }
            
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
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        Set<String> remove = new HashSet<String>();
        //check if it is embed form used in form grid
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null && request.getRequestURL().toString().contains("/form/embed")) {
            FormRowSet rowSet = null;
            Form form = FormUtil.findRootForm(this);
            String id = getPropertyString(FormUtil.PROPERTY_ID);

            if ("true".equals(getPropertyString("removeFile"))) {
                remove = new HashSet<String>();
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
                    List<String> tempResultedValue = new ArrayList<String>();
                    List<String> tempFilePaths = new ArrayList<String>();
                             
                    for (String value : values) {                 
                        // check if the file is in temp file
                        File file = FileManager.getFileByPath(value);
                        if (file != null) {
                            tempFilePaths.add(value);
                            tempResultedValue.add(file.getName()); 
                        } else {
                            if (remove != null && !value.isEmpty()) {
                                remove.remove(value);
                            }
                            tempResultedValue.add(value);
                        }                                          
                    }      
                    
                    if(tempResultedValue.isEmpty()){
                        for (String value : values) {                 
                            result.setProperty(id, value);
                            rowSet = new FormRowSet();
                            rowSet.add(result);                             
                        }  
                    } else {
                        // formulate values
                        String delimitedValue = FormUtil.generateElementPropertyValues(tempResultedValue.toArray(new String[]{}));
                        // set temp value into Properties and FormRowSet object
                        result.setProperty(id, delimitedValue);
                        rowSet = new FormRowSet();
                        rowSet.add(result);
                    }

                    
                    String filePathPostfix = "_path";
                    formData.addRequestParameterValues(id + filePathPostfix, new String[]{});
                }
            }

            return rowSet;
        }
        DMSOpenKMUtil openkmUtil = new DMSOpenKMUtil();
        String username = getPropertyString("username");
        String password = getPropertyString("password");
        String openkmURL = getPropertyString("openkmURL");
        if (openkmURL.endsWith("/")) {
            openkmURL = openkmURL.substring(0, openkmURL.length() - 1);
        }
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
            LogUtil.error(this.getClassName(), e, "Error parsing OpenKM URL in formatData: " + e.getMessage());
        }
        
        FormRowSet rowSet = null;

        String id = getPropertyString(FormUtil.PROPERTY_ID);
        
        Set<String> existing = new HashSet<String>();
        Form form = new Form();
        form = FormUtil.findRootForm(this);
        String originalValues = formData.getLoadBinderDataProperty(form, id);
        if (originalValues != null) {
            if ("true".equals(getPropertyString("removeFile"))) {
                remove.addAll(Arrays.asList(originalValues.split(";")));
            } else {
                existing.addAll(Arrays.asList(originalValues.split(";")));
            }
        } else {
            // if data is from child from instead of parent form
            FormData formData2 = new FormData();
            formData2.setPrimaryKeyValue(formData.getPrimaryKeyValue());
            Form loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), form.getPropertyString(FormUtil.PROPERTY_ID), null, null, null, formData2, null, null);
            Element el = FormUtil.findElement(getPropertyString(FormUtil.PROPERTY_ID), loadForm, formData2);
            String formvalue = FormUtil.getElementPropertyValue(el, formData2);
            if (formvalue != null) {
                if ("true".equals(getPropertyString("removeFile"))) {
                    remove.addAll(Arrays.asList(formvalue.split(";")));
                } else {
                    existing.addAll(Arrays.asList(formvalue.split(";")));
                }
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
                List<File> filesList = new ArrayList<>();

                folderName =  formData.getPrimaryKeyValue();

                for (String value : values) {
                    // check if the file is in temp file
                    File file = FileManager.getFileByPath(value);
                
                    if (file != null) {
                        filesList.add(file);
                    } else {
                        if(!value.isEmpty()){
                            if (remove != null && !remove.isEmpty() && !remove.contains("")) {
                                remove.removeIf(item -> {
                                        if (item.contains(value)) {
                                            resultedValue.add(item);
                                            return true;
                                        }
                                    return false;
                                });
                            } else {
                                existing.removeIf(item -> {
                                    if (item.contains(value)) {
                                        resultedValue.add(item);
                                        return true;
                                    }
                                    return false;
                                });
                                folderName = "";
                            }
                        }
                    }
                }

                // create new version instead of replace file
                List<String> checkoutDocs = new ArrayList<String>(); 
                Set<String> update = new HashSet<String>(remove);
                if (filesList != null && !filesList.isEmpty()) {
                    for (File file : filesList) {
                        for (String r : update) {
                            Map<String, String> fileMap = parseFileName(r);
                            documentId = fileMap.get("documentId");
                            if(r.contains(file.getName())){
                                if (getPropertyString("sameFileUpload").equals("version")) {
                                    // checkout file to replace version
                            ApiResponse checkoutApiResponse = openkmUtil.getApi(openkmURL + "/services/rest/document/checkout?docId=" + documentId, username, password, openkmURLHost, openkmURLPort);
                            if (checkoutApiResponse != null && checkoutApiResponse.getResponseCode() == 204) {
                                checkoutDocs.add(r);
                                remove.remove(r);
                            } else {
                                DMSOpenKMUtil.logApiError("Failed to checkout document from OpenKM", checkoutApiResponse);
                            }
                                }
                            }
                        }
                    }
                }

                if (remove != null && !remove.isEmpty() && !remove.contains("")) {
                    result.putDeleteFilePath(id, remove.toArray(new String[]{}));
                    for (String r : remove) {
                        Map<String, String> fileMap = parseFileName(r);
                        documentId = fileMap.get("documentId");
                       
                        
                        // delete file(s) from openkm
                        ApiResponse deleteFileApiResponse = openkmUtil.deleteApi(openkmURL + "/services/rest/document/delete?docId=" + documentId, username, password, openkmURLHost, openkmURLPort);    
                        if (deleteFileApiResponse != null && deleteFileApiResponse.getResponseCode() != 204) {
                            DMSOpenKMUtil.logApiError("Failed to delete file from OpenKM", deleteFileApiResponse);
                        }

                        // delete folder that contains the file above
                        if (filesList.isEmpty() && resultedValue.size() == 0) {
                            if ("true".equals(getPropertyString("removeFolder"))) {
                                ApiResponse getFolderIdApiResponse = openkmUtil.getApi(openkmURL + "/services/rest/repository/getNodeUuid?nodePath=" + openkmFileUploadPath, username, password, openkmURLHost, openkmURLPort);
                                if (getFolderIdApiResponse != null && getFolderIdApiResponse.getResponseCode() == 200) {
                                    String folderId = getFolderIdApiResponse.getResponseBody();

                                    ApiResponse deleteFolderApiResponse = openkmUtil.deleteApi(openkmURL + "/services/rest/folder/delete?fldId=" + folderId, username, password, openkmURLHost, openkmURLPort);    
                                    if (deleteFolderApiResponse != null && deleteFolderApiResponse.getResponseCode() != 204) {
                                        DMSOpenKMUtil.logApiError("Failed to delete folder from OpenKM", deleteFolderApiResponse);
                                    } else {
                                        folderName = "";
                                    }
                                } else {
                                    DMSOpenKMUtil.logApiError("Failed to get folder ID from OpenKM", getFolderIdApiResponse);
                                }
                            }
                        }
                    }
                }

                if (filesList != null && !filesList.isEmpty()) {
                    for (File file : filesList) {
                        boolean fileProcessed = false;
                        // create folder in OpenKM
                        if(createFolderFormID.equals("true")){
                            if (!openkmFileUploadPath.contains(folderName)) {
                                openkmUtil.createFolderApi(openkmURL + "/services/rest/folder/createSimple",username, password, openkmURLHost, openkmURLPort, folderName, openkmFileUploadPath);
                            } else {
                                folderName = "";
                            }
                        } else {
                            folderName = "";
                        }
                     
                        // file deleted and create new file
                        if (getPropertyString("sameFileUpload").equals("replace")){
                            documentId = openkmUtil.createFileApi(openkmURL + "/services/rest/document/createSimple", username, password, openkmURLHost, openkmURLPort, file.getName(), file, folderName, openkmFileUploadPath);    
                            if (documentId != null){
                                LogUtil.info(getClassName(), "Successfully replaced file \"" + file.getName() + "\" at OpenKM");
                            }
                        }  else if (getPropertyString("sameFileUpload").equals("version")){
                             // if successful checkout, only checkin (update file)
                            if (!checkoutDocs.isEmpty() && checkoutDocs.size() != 0) {
                                for (String checkoutDoc : checkoutDocs) {
                                    if(checkoutDoc.contains(file.getName())){
                                        Map<String, String> fileMap = parseFileName(checkoutDoc);
                                        documentId = fileMap.get("documentId");
                                        String version = openkmUtil.updateFileAfterCheckoutApi(openkmURL + "/services/rest/document/checkin", username, password, openkmURLHost, openkmURLPort, file.getName(), file, documentId);    
                                        LogUtil.info(getClassName(), "Successfully updated file version of \"" + version + "\" for file \"" + file.getName() + "\" at OpenKM");
                                        fileProcessed = true;
                                        break;
                                    }
                                }
                            }

                            if (!fileProcessed) { 
                                // create new file
                                documentId = openkmUtil.createFileApi(openkmURL + "/services/rest/document/createSimple", username, password, openkmURLHost, openkmURLPort, file.getName(), file, folderName, openkmFileUploadPath);    
                                if (documentId != null){
                                    LogUtil.info(getClassName(), "Successfully created file \"" + file.getName() + "\" at OpenKM");
                                }
                            }
                        }

                        if (documentId == null){
                            // document creation failed (e.g. 404 or other error from OpenKM)
                            formData.addFormError(id, "Failed to upload file \"" + file.getName() + "\" to OpenKM.");
                        } else {
                            // lock file
                            if (getPropertyString("lockFile").equals("true")){
                                ApiResponse checkoutApiResponse = openkmUtil.putApi(openkmURL + "/services/rest/document/lock?docId=" + documentId, username, password, openkmURLHost, openkmURLPort);
                                if (checkoutApiResponse != null && checkoutApiResponse.getResponseCode() == 200) {
                                    LogUtil.info(getClassName(), "Successfully lock file \"" + file.getName() + "\" at OpenKM");
                                } else {
                                    DMSOpenKMUtil.logApiError("Failed to lock document in OpenKM", checkoutApiResponse);
                                }
                            }

                            filePaths.add(file.getName() + "|" + documentId);
                            resultedValue.add(file.getName() + "|" + documentId);
                        }
                    }
                }

                if (!filePaths.isEmpty()) {
                    result.putTempFilePath(id, filePaths.toArray(new String[]{}));
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
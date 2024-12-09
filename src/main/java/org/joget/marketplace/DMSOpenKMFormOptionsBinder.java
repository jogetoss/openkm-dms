package org.joget.marketplace;

import java.net.URL;
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
import org.joget.marketplace.util.DMSOpenKMUtil;
import org.json.JSONArray;
import org.json.JSONObject;

public class DMSOpenKMFormOptionsBinder extends FormBinder implements FormLoadOptionsBinder {

    private final static String MESSAGE_PATH = "messages/DMSOpenKMFormOptionsBinder";
    
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmformoptionsbinder.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getVersion() {
        return Activator.VERSION;
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
        DMSOpenKMUtil openkmUtil = new DMSOpenKMUtil();
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

        boolean formBuilderActive = FormUtil.isFormBuilderActive();

        if (!formBuilderActive) {
            ApiResponse getRootFolderApiResponse = openkmUtil.getApi(openkmURL + "/services/rest/folder/getPath/" + folderRootID, username, password, openkmURLHost, openkmURLPort);
            if (getRootFolderApiResponse != null && getRootFolderApiResponse.getResponseCode() == 200) {
                String rootPath = getRootFolderApiResponse.getResponseBody();
                FormRow emptyRow = new FormRow();
                emptyRow.setProperty(FormUtil.PROPERTY_VALUE, rootPath);
                emptyRow.setProperty(FormUtil.PROPERTY_LABEL, rootPath);
                emptyRow.setProperty(FormUtil.PROPERTY_GROUPING, "");
                results.add(emptyRow);
            }
        
            results = getChildrenFolders(results, openkmURL, folderRootID, username, password, openkmURLHost, openkmURLPort);

        }
        return results;
    }

      public FormRowSet getChildrenFolders(FormRowSet results, String openkmURL, String folderRootID, String username, String password, String openkmURLHost,Integer openkmURLPort) {
        DMSOpenKMUtil openkmUtil = new DMSOpenKMUtil();
        ApiResponse getChildrenFoldersApiResponse = openkmUtil.getApi(openkmURL + "/services/rest/folder/getChildren?fldId=" + folderRootID, username, password, openkmURLHost, openkmURLPort);
        if(getChildrenFoldersApiResponse.getResponseBody().startsWith("{")){
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
        } else {
            FormRow emptyRow = new FormRow();
            emptyRow.setProperty(FormUtil.PROPERTY_VALUE, "Unable to retrieve value from OpenKM");
            emptyRow.setProperty(FormUtil.PROPERTY_LABEL, "Unable to retrieve value from OpenKM");
            results.add(emptyRow);
        }
        return results;
    }

    public void getFolderObject(JSONObject folderObject, FormRowSet results, String openkmURL, String folderRootID, String username, String password, String openkmURLHost,Integer openkmURLPort) {
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
}
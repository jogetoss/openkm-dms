package org.joget.marketplace;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import javax.servlet.http.HttpServletRequest;
import org.displaytag.util.LookupUtil;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListColumnFormatDefault;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.StringUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;

public class DMSOpenKMDatalistFormatter extends DataListColumnFormatDefault {
    
    private final static String MESSAGE_PATH = "messages/DMSOpenKMDatalistFormatter";
    
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmdatalistformatter.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    public String getVersion() {
        return "8.0.0";
    }
    
    public String getClassName() {
        return getClass().getName();
    }

    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmdatalistformatter.pluginLabel", getClassName(), MESSAGE_PATH);
    }
    
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.marketplace.openkmdatalistformatter.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DMSOpenKMDatalistFormatter.json", null, true, MESSAGE_PATH);
    }

    public String format(DataList dataList, DataListColumn column, Object row, Object value) {
        String result = (String) value;
        if (result != null && !result.isEmpty()) {
            try {
                String username = getPropertyString("username");
                String password = getPropertyString("password");
                String openkmURL = getPropertyString("openkmURL");
                String protocol = "";
                String hostAndPort = "";
                String formDefId = getPropertyString("formDefId");
                String openkmFileUploadPathField = getPropertyString("openkmFileUploadPath");
        
                try {
                    URL url = new URL(openkmURL);
                    protocol = url.getProtocol(); 
                    hostAndPort = url.getHost() + (url.getPort() != -1 ? (":" + url.getPort()) : "");
                } catch (Exception e) {
                    LogUtil.error(this.getClassName(), e, e.getMessage());
                }

                String openkmFileUploadPath = (String) LookupUtil.getBeanProperty(row, openkmFileUploadPathField);
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                result = "";

                //get the id of this record
                String primaryKeyValue = (String) LookupUtil.getBeanProperty(row, dataList.getBinder().getPrimaryKeyColumnName());
                
                HttpServletRequest request = WorkflowUtil.getHttpServletRequest();

                //suport for multi values
                for (String v : value.toString().split(";")) {
                    if (!v.isEmpty()) {
                        // determine actual path for the file uploads
                        String fileName = v;
                        String encodedFileName = fileName;
                        try {
                            encodedFileName = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
                        } catch (UnsupportedEncodingException ex) {
                            // ignore
                        }

                        JSONObject jsonParams = new JSONObject();
                        jsonParams.put("protocol", protocol);
                        jsonParams.put("username", username);
                        jsonParams.put("password", password);
                        jsonParams.put("hostAndPort", hostAndPort);
                        jsonParams.put("openkmFileUploadPath", openkmFileUploadPath);
                        jsonParams.put("fileName", v);
                        String params = StringUtil.escapeString(SecurityUtil.encrypt(jsonParams.toString()), StringUtil.TYPE_URL, null);

                        String filePath = request.getContextPath() + "/web/json/app/" + appDef.getAppId() + "/" + appDef.getVersion().toString() + "/plugin/org.joget.marketplace.DMSOpenKMFileUpload/service?action=download&params=" + params;

                        if (!result.isEmpty()) {
                            result += ", ";
                        }

                        result += "<a href=\""+filePath+"\" target=\"_blank\">"+StringUtil.stripAllHtmlTag(fileName)+"</a>";
                    }
                }
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "");
            }
        }
        return result;
    }
}

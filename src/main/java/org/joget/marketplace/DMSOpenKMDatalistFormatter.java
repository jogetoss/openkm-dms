package org.joget.marketplace;

import java.net.URL;
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
        return Activator.VERSION;
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
        StringBuilder result = new StringBuilder();
        if (value != null) {
            String[] values = value.toString().split(";");
            try {
                AppDefinition appDef = AppUtil.getCurrentAppDefinition();
                HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
                String username = getPropertyString("username");
                String password = getPropertyString("password");
                String openkmURL = getPropertyString("openkmURL");
                String enableDownload = getPropertyString("enableDownload");
                String protocol = "";
                String hostAndPort = "";
                String openkmFileUploadPathField = getPropertyString("openkmFileUploadPath");
                String openkmFileUploadPath = "";
        
                try {
                    URL url = new URL(openkmURL);
                    protocol = url.getProtocol(); 
                    hostAndPort = url.getHost() + (url.getPort() != -1 ? (":" + url.getPort()) : "");
                } catch (Exception e) {
                    LogUtil.error(this.getClassName(), e, e.getMessage());
                }

                // get file path of this record
                if (openkmFileUploadPathField != null && !openkmFileUploadPathField.equals("")) {
                    openkmFileUploadPath = (String) LookupUtil.getBeanProperty(row, openkmFileUploadPathField);
                } else {
                    openkmFileUploadPath = "/okm:root";
                }

                JSONObject jsonParams = new JSONObject();
                jsonParams.put("protocol", protocol);
                jsonParams.put("username", username);
                jsonParams.put("password", password);
                jsonParams.put("hostAndPort", hostAndPort);
                jsonParams.put("openkmFileUploadPath", openkmFileUploadPath);

                for (String v : values) {
                    if (v != null && !v.isEmpty()) {
                        String[] verticalBarSplit = v.split("\\|");
                        if (verticalBarSplit.length > 0) {
                            String filename = verticalBarSplit[0];
                            String documentId = verticalBarSplit[1];
                            jsonParams.put("fileName", filename);
                            String params = StringUtil.escapeString(SecurityUtil.encrypt(jsonParams.toString()), StringUtil.TYPE_URL, null);
                            if ("true".equalsIgnoreCase(enableDownload)) {
                                String filePath = request.getContextPath() + "/web/json/app/" + appDef.getAppId() + "/" + appDef.getVersion().toString() + "/plugin/org.joget.marketplace.DMSOpenKMFileUpload/service?action=download&params=" + params;
                                String downloadUrl = "<a href=\"" + filePath + "\" target=\"_blank\">" + filename + "</a>";
                                result.append(downloadUrl);
                            } else {
                                result.append(filename);
                            }
                            result.append(";");
                        }
    
                    } else {
                        result.append(v);
                    }
    
                }
                if (result.length() > 0) {
                    result.deleteCharAt(result.length() - 1);
                }
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "");
            }
        }
        return result.toString();
    }
}

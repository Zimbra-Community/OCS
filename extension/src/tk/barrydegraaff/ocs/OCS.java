/*

Copyright (C) 2017  Barry de Graaff

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see http://www.gnu.org/licenses/.

*/
package tk.barrydegraaff.ocs;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.DocumentHandler;
import com.zimbra.soap.ZimbraSoapContext;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class OCS extends DocumentHandler {
    public Element handle(Element request, Map<String, Object> context)
            throws ServiceException {
        try {
            ZimbraSoapContext zsc = getZimbraSoapContext(context);
            Element response = zsc.createElement(
                    "response"
            );
            switch (request.getAttribute("action")) {
                case "createShare":
                    return createShare(request, response);
                default:
                    return (response);
            }
        } catch (
                Exception e) {
            throw ServiceException.FAILURE("exception occurred handling command", e);
        }
    }

    private Element createShare(Element request, Element response) {
        try {
            if (checkPermissionOnTarget(request.getAttribute("owncloud_zimlet_server_name"))) {
                String urlParameters = "path=" + request.getAttribute("path") + "&shareType=" + request.getAttribute("shareType") + "&password=" + request.getAttribute("password");

                byte[] credentials = Base64.encodeBase64((uriDecode(request.getAttribute("owncloud_zimlet_username")) + ":" + uriDecode(request.getAttribute("owncloud_zimlet_password"))).getBytes());
                byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
                int postDataLength = postData.length;

                String requestUrl = request.getAttribute("owncloud_zimlet_server_name") + ":" + request.getAttribute("owncloud_zimlet_server_port") + request.getAttribute("owncloud_zimlet_oc_folder") + "/ocs/v1.php/apps/files_sharing/api/v1/shares";
                URL url = new URL(requestUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setDoOutput(true);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("charset", "utf-8");
                conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                conn.setRequestProperty("OCS-APIRequest", "true");
                conn.setRequestProperty("Authorization", "Basic " + new String(credentials));
                conn.setUseCaches(false);

                try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                    wr.write(postData);
                }

                InputStream _is;
                Boolean isError = false;
                if (conn.getResponseCode() < 400) {
                    _is = conn.getInputStream();
                    isError = false;
                } else {
                    _is = conn.getErrorStream();
                    isError = true;
                }

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(_is));

                String inputLine;
                StringBuffer responseTxt = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    responseTxt.append(inputLine);
                }
                in.close();

                Pattern pattern;
                if (isError) {
                    pattern = Pattern.compile("<message>(.+?)</message>");
                } else {
                    pattern = Pattern.compile("<url>(.+?)</url>");
                }
                Matcher matcher = pattern.matcher(responseTxt.toString());
                matcher.find();
                final String result = matcher.group(1);

                if (!isError) {
                    pattern = Pattern.compile("<id>(.+?)</id>");
                }
                matcher = pattern.matcher(responseTxt.toString());
                matcher.find();
                final String id = matcher.group(1);

                //Implement Expiry date, in the future, add this to a loop so we can update more share properties as defined in
                //https://docs.nextcloud.com/server/12/developer_manual/core/ocs-share-api.html#update-share
                try {
                    if (request.getAttribute("expiry_date").length() == 10) {
                        urlParameters = "expireDate="+request.getAttribute("expiry_date");

                        postData = urlParameters.getBytes(StandardCharsets.UTF_8);
                        postDataLength = postData.length;

                        requestUrl = request.getAttribute("owncloud_zimlet_server_name") + ":" + request.getAttribute("owncloud_zimlet_server_port") + request.getAttribute("owncloud_zimlet_oc_folder") + "/ocs/v1.php/apps/files_sharing/api/v1/shares"+ "/" + id;

                        url = new URL(requestUrl);
                        conn = (HttpURLConnection) url.openConnection();

                        conn.setDoOutput(true);
                        conn.setInstanceFollowRedirects(true);
                        conn.setRequestMethod("PUT");
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        conn.setRequestProperty("charset", "utf-8");
                        conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
                        conn.setRequestProperty("OCS-APIRequest", "true");
                        conn.setRequestProperty("Authorization", "Basic " + new String(credentials));
                        conn.setUseCaches(false);

                        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                            wr.write(postData);
                        }

                        isError = false;
                        if (conn.getResponseCode() < 400) {
                            _is = conn.getInputStream();
                            isError = false;
                        } else {
                            _is = conn.getErrorStream();
                            isError = true;
                        }

                        in = new BufferedReader(
                                new InputStreamReader(_is));

                        responseTxt = new StringBuffer();

                        while ((inputLine = in.readLine()) != null) {
                            responseTxt.append(inputLine);
                        }
                        in.close();
                        //to-do deal with the response, and errors.

                    }
                } catch (Exception e) {
                    //ignore
                }

                response.addAttribute("createShare", "{\"statuscode\":100,\"id\":\"" + id + "\",\"message\":\"\",\"url\":\"" + result + "\",\"status\":\"ok\",\"token\":\"\"}");
            } else {
                response.addAttribute("createShare", "{\"statuscode\":100,\"id\":0,\"message\":\"\",\"url\":\"" + "Host not allowed: " + request.getAttribute("owncloud_zimlet_server_name") + "\",\"status\":\"ok\",\"token\":\"\"}");
            }
            return response;
        } catch (Exception ex) {
            response.addAttribute("createShare", "{\"statuscode\":100,\"id\":0,\"message\":\"\",\"url\":\"" + "Could not create share. " + "\",\"status\":\"ok\",\"token\":\"\"}");
            return response;
        }
    }

    private String uriDecode(String dirty) {
        try {
            String clean = java.net.URLDecoder.decode(dirty, "UTF-8");
            return clean;
        } catch (Exception ex) {
            return ex.toString();
        }
    }

    private boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public static boolean checkPermissionOnTarget(String host) {

        Properties prop = new Properties();
        try {
            FileInputStream input = new FileInputStream("/opt/zimbra/lib/ext/ownCloud/config.properties");
            prop.load(input);

            String[] temp = prop.getProperty("allowdomains").split(";");
            Set<String> domains = new HashSet<String>(Arrays.asList(temp));

            input.close();

            for (String domain : domains) {
                if (domain.equals("*")) {
                    return true;
                }
                if (domain.charAt(0) == '*') {
                    domain = domain.substring(1);
                }
                if (host.endsWith(domain)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
    }

}
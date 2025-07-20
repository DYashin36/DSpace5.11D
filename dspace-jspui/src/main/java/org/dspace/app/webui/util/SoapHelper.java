package org.dspace.app.webui.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.dspace.app.webui.servlet.admin.EditCommunitiesServlet;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Created by root on 1/1/16.
 */
public class SoapHelper {

    private static Logger log = Logger.getLogger(EditCommunitiesServlet.class);

    public Document getRecordByCode(String id){
        
        HttpURLConnection connection = null;
        URL url = null;
        log.info("SH>>getRecordByCode>>ID:"+id);
        try {
            url = new URL("http://localhost:8888/soap");
            log.info("SUCCESSFULL CONNECTING SOAP");
        } catch (MalformedURLException e) {
            log.info("ERROR CONNECTING SOAP");
            e.printStackTrace();
        }

        try {
            connection = (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connection.setRequestProperty("Authorization", "Basic d2Vic2VydmljZTp3ZWJzZXJ2aWNl");
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        connection.setRequestProperty("Accept-Encoding", "gzip,deflate");
        try {
            connection.setRequestMethod("POST");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }
        connection.setDoOutput(true);

        DataOutputStream wr = null;
        try {
            wr = new DataOutputStream(
                    connection.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Charset charset = Charset.forName("UTF-8");
        try {

            wr.write(new String("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:bibl=\"http://www.omega-spb.ru/bibl\">\n" +
                    "   <soap:Header/>\n" +
                    "   <soap:Body>\n" +
                    "      <bibl:GetRecordList>\n" +
                    "         <bibl:CodeName>ID</bibl:CodeName>\n" +
                    "         <bibl:CodesList>" + id + "</bibl:CodesList>\n" +
                    "         <bibl:Separator>,</bibl:Separator>\n" +
                    "         <bibl:needRUSMARC>false</bibl:needRUSMARC>\n" +
                    "         <bibl:needKatalogCard>false</bibl:needKatalogCard>\n" +
                    "         <bibl:needCopies>false</bibl:needCopies>\n" +
                    "         <bibl:needSumInfo>false</bibl:needSumInfo>\n" +
                    "         <bibl:needlemma>false</bibl:needlemma>\n" +
                    "      </bibl:GetRecordList>\n" +
                    "   </soap:Body>\n" +
                    "</soap:Envelope>").getBytes(charset));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            wr.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream is = null;
        try {
            is = connection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
        String line;
        String responseString = "";
        try {
            while((line = rd.readLine()) != null) {
                responseString+=line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            rd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        DocumentBuilder db = null;

        try {
            db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        InputSource is2 = new InputSource();
        is2.setCharacterStream(new StringReader(responseString));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        try {
            log.info("===> XML \n " + responseString);
            return builder.parse(new ByteArrayInputStream(responseString.getBytes()));
        } catch (SAXException e) {
            log.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public Document getRecordByName(String name, String title){
        HttpURLConnection connection = null;
        URL url = null;
        log.info("SH>>getRecordByName was called");
        String authors = "(Автор Равно "+name+")";
        String titles = "(Заглавие Содержит "+title+")";
        log.info("SH>>getRecordByName>>name:"+name+";title:"+title);

        if(name == null){
            authors = "";
        }
        if(title == null){
            titles = "";
        }


        try {
            url = new URL("http://localhost:8888/soap");
            log.info("SH>>getRecordByName>>SUCCESSFULL CREATING URL");
        } catch (MalformedURLException e) {
            log.info("SH>>getRecordByName>>ERROR occurred when creating an url");
            e.printStackTrace();
        }

        try {
            connection = (HttpURLConnection)url.openConnection();
            log.info("SH>>getRecordByName>>SUCCESSFULL OPEN CONNECTION");
        } catch (IOException e) {
            e.printStackTrace();
        }
        connection.setRequestProperty("Authorization", "Basic d2Vic2VydmljZTp3ZWJzZXJ2aWNl");
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        connection.setRequestProperty("Accept-Encoding", "gzip,deflate");
        try {
            connection.setRequestMethod("POST");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }
        connection.setDoOutput(true);

        DataOutputStream wr = null;
        try {
            wr = new DataOutputStream(
                    connection.getOutputStream());
                    log.info("SH>>getRecordByName>>OUS was received");
        } catch (IOException e) {
            log.info("SH>>getRecordByName>>Error occurred when get the ous");
            e.printStackTrace();
        }

        Charset charset = Charset.forName("UTF-8");
        try {

            wr.write(new String("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:bibl=\"http://www.omega-spb.ru/bibl\">\n" +
                    "   <soap:Header/>\n" +
                    "   <soap:Body>\n" +
                    "      <bibl:GetShortRecordList>\n" +
                    "         <bibl:SearchExpression>" + authors + " " + titles + "(DSpace Равно Истина)(Диапазон 1-25)</bibl:SearchExpression>\n" +
                    "         <bibl:Size></bibl:Size>\n" +
                    "         <bibl:Direction></bibl:Direction>\n" +
                    "         <bibl:CurrentPosition></bibl:CurrentPosition>\n" +
                    "      </bibl:GetShortRecordList>\n" +
                    "   </soap:Body>\n" +
                    "</soap:Envelope>").getBytes(charset));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            wr.close();
            log.info("SH>>getRecordByName>>OUS was closed");
        } catch (IOException e) {
            log.info("SH>>getRecordByName>>ERror closing the wr "+e.getMessage());
            e.printStackTrace();
        }

        InputStream is = null;
        try {
            is = connection.getInputStream();
            log.info("SH>>getRecordByName>>IS received");
        } catch (IOException e) {
            log.info("SH>>getRecordByName>>Error occurred in IS receicing");
            e.printStackTrace();
        }
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
        String line;
        String responseString = "";
        try {
            while((line = rd.readLine()) != null) {
                responseString+=line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            rd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        DocumentBuilder db = null;

        try {
            db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            log.info("SH>>getRecordByName>>DocumentBuilderFactory created");
        } catch (ParserConfigurationException e) {
            log.info("SH>>getRecordByName>>DocumentBuilderFactory creation ERROR");
            e.printStackTrace();
        }
        InputSource is2 = new InputSource();
        is2.setCharacterStream(new StringReader(responseString));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        try {
            log.info("===> XML \n " + responseString);
            return builder.parse(new ByteArrayInputStream(responseString.getBytes()));
        } catch (SAXException e) {
            log.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    public Document getRecordById(String id){
        HttpURLConnection connection = null;
        URL url = null;
        log.info("SH>>getRecordById>> id="+id);
        try {
            url = new URL("http://localhost:8888/soap");
            log.info("SUCCESSFULL CREATING URL");
        } catch (MalformedURLException e) {
            log.info("ERROR CREATING URL");
            e.printStackTrace();
        }

        try {
            connection = (HttpURLConnection)url.openConnection();
            log.info("SUCCESSFULL CONNECTING SOAP");
        } catch (IOException e) {
            log.info("ERROR CONNECTING SOAP");
            e.printStackTrace();
        }
        connection.setRequestProperty("Authorization", "Basic d2Vic2VydmljZTp3ZWJzZXJ2aWNl");
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        connection.setRequestProperty("Accept-Encoding", "gzip,deflate");
        try {
            connection.setRequestMethod("POST");
            log.info("SET REQUEST METHOD POST");
        } catch (ProtocolException e) {
            log.info("EXCEPTION WHILE SET REQUEST METHOD POST");
            e.printStackTrace();
        }
        connection.setDoOutput(true);
        log.info("SUCCESSFULL SET DO OUTPUT");
        DataOutputStream wr = null;
        try {
            wr = new DataOutputStream(
                    connection.getOutputStream());
                    log.info("DOS received");
        } catch (IOException e) {
            log.info("error while get DOS");
            e.printStackTrace();
        }

        Charset charset = Charset.forName("UTF-8");
        try {

            wr.write(new String("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:imc=\"http://imc.parus-s.ru\">\n" +
                    "   <soap:Header/>\n" +
                    "   <soap:Body>\n" +
                    "      <imc:GetRecordsInfo>\n" +
                    "         <imc:Codes>"+id+"</imc:Codes>\n" +
                    "         <imc:Separator>?</imc:Separator>\n" +
                    "         <imc:Type>?</imc:Type>\n" +
                    "      </imc:GetRecordsInfo>\n" +
                    "   </soap:Body>\n" +
                    "</soap:Envelope>").getBytes(charset));
            log.info(">>Successfull write string to DOS");
        } catch (IOException e) {
            log.info(">>Error while write string to DOS");
            e.printStackTrace();
        }
        try {
            wr.close();
            log.info(">>DOS closed");
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream is = null;
        try {
            is = connection.getInputStream();
            log.info(">>IS opened");
        } catch (IOException e) {
            log.info(">>error occured while opening IS");
            e.printStackTrace();
        }
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
        String line;
        String responseString = "";
        try {
            while((line = rd.readLine()) != null) {
                responseString+=line;
            }
        } catch (IOException e) {
            log.info(">>error occured while reading rd; curret line="+responseString);
            e.printStackTrace();
        }
        try {
            rd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        DocumentBuilder db = null;

        try {
            db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        InputSource is2 = new InputSource();
        is2.setCharacterStream(new StringReader(responseString));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        DocumentBuilder builder = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }

        try {
            log.info("===> XML \n " + responseString);
            return builder.parse(new ByteArrayInputStream(responseString.getBytes()));
        } catch (SAXException e) {
            log.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            return null;
        }



        //return responseString;
    }

    public void writeLink(String iden, String link){
        HttpURLConnection connection = null;
        URL url = null;
        try {
            url = new URL("http://localhost:8888/soap");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        try {
            connection = (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connection.setRequestProperty("Authorization", "Basic d2Vic2VydmljZTp3ZWJzZXJ2aWNl");
        connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        connection.setRequestProperty("Accept-Encoding", "gzip,deflate");
        try {
            connection.setRequestMethod("POST");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }
        connection.setDoOutput(true);

        DataOutputStream wr = null;
        try {
            wr = new DataOutputStream(
                    connection.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Charset charset = Charset.forName("UTF-8");
        try {

            wr.write(new String("<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:imc=\"http://imc.parus-s.ru\" xmlns:imc1=\"http://www.imc-dspace.org\">\n" +
                    "   <soap:Header/>\n" +
                    "   <soap:Body>\n" +
                    "      <imc:PutRecordsInfo>\n" +
                    "         <imc:Records>\n" +
                    "            <!--Zero or more repetitions:-->\n" +
                    "            <imc1:Records>\n" +
                    "               <imc1:Identifier>\n" +
                    "                  <imc1:Qualifier>Identifier</imc1:Qualifier>\n" +
                    "                  <imc1:Value>"+iden+"</imc1:Value>\n" +
                    "               </imc1:Identifier>\n" +
                    "               <imc1:Link>"+link+"</imc1:Link>\n" +
                    "            </imc1:Records>\n" +
                    "         </imc:Records>\n" +
                    "      </imc:PutRecordsInfo>\n" +
                    "   </soap:Body>\n" +
                    "</soap:Envelope>").getBytes(charset));
        } catch (IOException e) {
            log.info("smth is wrong 1");
        }
        try {
            wr.close();
        } catch (IOException e) {
            log.info("smth is wrong 2");
        }
        InputStream is = null;
        try {
            is = connection.getInputStream();
        } catch (IOException e) {
            log.info("smth is wrong 3");
        }

        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        StringBuilder response = new StringBuilder(); // or StringBuffer if not Java 5+
        String line;
        String responseString = "";
        try {
            while((line = rd.readLine()) != null) {
                responseString+=line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            rd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("LINKTOWRITE: "+link);
        log.info("RESPONSSTRING: "+responseString);
    }



}

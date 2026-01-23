package org.dspace.app.webui.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileDeleteStrategy;
import org.apache.log4j.Logger;
import org.dspace.app.webui.servlet.admin.EditCommunitiesServlet;
import org.dspace.app.webui.util.SoapHelper;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Collection;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.Metadatum;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.workflow.WorkflowManager;
import org.dspace.xmlworkflow.XmlWorkflowManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Created by root on 1/12/16.
 */
public class ImportMassServlet extends DSpaceServlet {

    private static Logger log = Logger.getLogger(EditCommunitiesServlet.class);

    public static final String UTF8_BOM = "\uFEFF";

    protected void doDSGet(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException {
        //log.info("ImportMassServlet>>doDSGet>>Here is import-mass DSget enter");
        Collection[] col = Collection.findAllWithoutWorkflow(context);

        TableRowIterator tri = DatabaseManager.queryTable(context, "folders", "SELECT * FROM folders");
        request.setAttribute("systems", tri);

        ArrayList<String> ids = new ArrayList<>();

        request.setAttribute("ids", col);

        response.setCharacterEncoding("UTF-8");
        request.setCharacterEncoding("UTF-8");
        //log.info("ImportMassServlet>>doDSGet>>Redirect to import/import-mass-home");
        request.getRequestDispatcher("/import/import-mass-home.jsp").forward(request, response);
    }

    protected void doDSPost(Context context, HttpServletRequest request,
        HttpServletResponse response) throws ServletException, IOException,
        SQLException, AuthorizeException {
    //log.info("ImportMassServlet>>doDSPost>>Here is import-mass DSPost enter");
    String folder = request.getParameter("folder_path");
    String collectionId = request.getParameter("collection_id");
    //log.info("ImportMassServlet>>doDSPost>>received params: folder=" + folder + "; collectionId=" + collectionId);

    File dir = null;
    File[] directoryListing = null;

    try {
        dir = new File(folder);
        directoryListing = dir.listFiles();
    } catch (Exception e) {
        //log.info("ImportMassServlet>>doDSPost>>error occurred when dir/file receiving");
        request.getRequestDispatcher("/import/import-no-file.jsp").forward(request, response);
    }

    if (directoryListing == null) {
        //log.info("ImportMassServlet>>doDSPost>>null directoryListing");
        request.getRequestDispatcher("/import/import-no-file.jsp").forward(request, response);
    }
    //log.info("ImportMassServlet>>doDSPost>>start to find a collection");
    Collection col = Collection.find(context, Integer.parseInt(collectionId));
    Integer lel = directoryListing.length;
    //log.info("ImportMassServlet>>doDSPost>>length of directoryListing is " + lel);
    log.debug("WTFDIRECTO " + lel.toString());
    int howManyWasSubmited = 0;

    ArrayList<String> links = new ArrayList<String>();

    if (directoryListing.length <= 0) {
        //log.info("ImportMassServlet>>doDSPost>>length of directoryListing is below 0");
        request.getRequestDispatcher("/import/import-no-file.jsp").forward(request, response);
    }
    if (directoryListing != null) {
        //log.info("ImportMassServlet>>doDSPost>>directoryListing is not null and eq to " + directoryListing.length);
        for (int j = 0; j < directoryListing.length; j++) {
            String absolutePath = directoryListing[j].getAbsolutePath();
            String filepath = absolutePath.
                    substring(0, absolutePath.lastIndexOf(File.separator));
            String filename = directoryListing[j].getName();
            log.info("IMS>>doDSPost>>now we seeing a " + filepath + "/" + filename);

            if (filename.toLowerCase().endsWith(".xml")) {
                try {
                    log.info(".xml is founded");
                    BufferedReader inputReader = new BufferedReader(new FileReader(filepath + "/" + filename));
                    StringBuilder sb = new StringBuilder();
                    String inline = "";
                    while ((inline = inputReader.readLine()) != null) {
                        sb.append(inline);
                    }
                    //inputReader.close();
                    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();

                    String dbString = sb.toString();
                    log.info("WTFLOL: " + dbString);
                    dbString = removeUTF8BOM(dbString);

                    InputSource is = new InputSource();
                    is.setCharacterStream(new StringReader(dbString));

                    Document doc = db.parse(is);
                    NodeList records = doc.getElementsByTagName("Records");
                    if (records.getLength() > 0) {
                        //log.info("ImportMassServlet>>doDSPost>>length of records is " + records.getLength());
                    }
                    howManyWasSubmited++;
                    log.info("howManyWasSubmitted is " + howManyWasSubmited);
                    for (int i = 0; i < records.getLength(); i++) {
                        try {
                            Element record = (Element) records.item(i);
                            Boolean exists = false;
                            Integer itemId = 0;

                            try {
                                NodeList identifier = record.getElementsByTagName("Identifier");
                                log.info("doDSPost>>Identifier is eq to " + identifier.getLength());
                                for (int k = 0; k < identifier.getLength(); k++) {
                                    Element subjectNode = (Element) identifier.item(k);
                                    Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
                                    Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
                                    log.info("TextContent; textSubject:" + textSubject.getTextContent() + "; qulSubject:" + qulSubject.getTextContent());
                                    if (qulSubject.getTextContent().toLowerCase().equals("identifier")) {
                                        TableRowIterator tri = DatabaseManager.queryTable(context, "metadatavalue", "SELECT resource_id, text_value FROM metadatavalue WHERE text_value='" + textSubject.getTextContent() + "'");
                                        log.info("TableRowIterator is " + tri);
                                        if (tri.hasNext()) {
                                            log.info("OKIGOTIT: ");

                                            exists = true;
                                            TableRow row = tri.next();
                                            log.info(row);
                                            itemId = row.getIntColumn("resource_id");
                                            log.info("OKIGOTIT: " + itemId.toString());
                                        }

                                    }
                                }
                                identifier = null;
                            } catch (Exception e) {
                                log.info("OKERROR: " + e);
                            }

                            WorkspaceItem wsitem = null;
                            Item itemItem = null;

                            if (exists == false) {
                                log.info("doDSPost>>createMass call");
                                wsitem = WorkspaceItem.createMass(context, col, false);
                                itemItem = wsitem.getItem();
                                //response.getWriter().write("test");

                                itemItem.setOwningCollection(col);
                            } else {
                                log.info("OKIGOTIT: " + itemId.toString());
                                itemItem = Item.find(context, itemId);
                                itemItem.clearDC(Item.ANY, Item.ANY, Item.ANY);
                                log.info("OKIGOTIT: " + itemId.toString());
                                itemItem.update();
                            }

                            try {
                                NodeList titleNode = record.getElementsByTagName("Title");
                                //log.info("doDSPost>>received Title is " + titleNode);
                                //  itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "title", null, "ru", tex.getTextContent());
                                writeMetaDataToItemNormalized(itemItem, "title", record.getElementsByTagName("Title"));
                                titleNode = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList identifier = record.getElementsByTagName("Identifier");
                                //log.info("doDSPost>>received Identifier is " + identifier);
                                //  itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "title", null, "ru", tex.getTextContent());
                                writeMetaDataToItemLowerCaseIdentifier(itemItem, "identifier", identifier);
                                identifier = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
    NodeList contributors = record.getElementsByTagName("Contributor");
    for (int c = 0; c < contributors.getLength(); c++) {
        Element contribElement = (Element) contributors.item(c);

        Node qualifierNode = contribElement.getElementsByTagName("Qualifier").item(0);
        Node valueNode = contribElement.getElementsByTagName("Value").item(0);

        if (valueNode != null && qualifierNode != null) {
            String value = valueNode.getTextContent().trim();
            String qualifier = qualifierNode.getTextContent().trim().toLowerCase();

            if (!value.isEmpty()) {
                // Добавляем всех контрибьюторов, сохраняем qualifier
                itemItem.addMetadata(
                    MetadataSchema.DC_SCHEMA,
                    "contributor",
                    qualifier.isEmpty() ? null : qualifier,
                    "ru",
                    value
                );
                log.info("Added contributor: " + value + " (qualifier: " + qualifier + ")");
            }
        }
    }
} catch (Exception e) {
    log.warn("Error while processing Contributor nodes: " + e.getMessage());
}


                            try {
                                NodeList subjects = record.getElementsByTagName("Subject");
                                //log.info("doDSPost>>received Subject is " + subjects);
                                writeMetaDataToItemNormalized(itemItem, "subject", record.getElementsByTagName("Subject"));
                                subjects = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList descrs = record.getElementsByTagName("Description");
                                //log.info("doDSPost>>received Description is " + descrs);
                                writeMetaDataToItemNormalized(itemItem, "description", record.getElementsByTagName("Description"));
                                descrs = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList dateNodes = record.getElementsByTagName("Date");
                                //log.info("doDSPost>>received Date count: " + dateNodes.getLength());
                                for (int d = 0; d < dateNodes.getLength(); d++) {
                                    Element dateElement = (Element) dateNodes.item(d);
                                    Node qualifierNode = dateElement.getElementsByTagName("Qualifier").item(0);
                                    Node valueNode = dateElement.getElementsByTagName("Value").item(0);

                                    if (valueNode != null) {
                                        String dateValue = valueNode.getTextContent().trim();
                                        if (!dateValue.isEmpty()) {
                                            itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "date", "issued", "ru", dateValue);
                                            log.info("Added date.issued: " + dateValue);
                                        } else {
                                            log.info("Skipped empty date value");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Error while processing Date field: " + e.getMessage());
                            }

                            writeMetaDataToItemNormalized(itemItem, "publisher", record.getElementsByTagName("Publisher"));
                            writeMetaDataToItemNormalized(itemItem, "type", record.getElementsByTagName("Type"));
                            writeMetaDataToItemNormalized(itemItem, "source", record.getElementsByTagName("Source"));
                            writeMetaDataToItemNormalized(itemItem, "rights", record.getElementsByTagName("Rights"));


                            // try {
                            //     NodeList publisherNodes = record.getElementsByTagName("Publisher");
                            //     for (int p = 0; p < publisherNodes.getLength(); p++) {
                            //         Element publisherElement = (Element) publisherNodes.item(p);
                            //         Node qualifierNode = publisherElement.getElementsByTagName("Qualifier").item(0);
                            //         Node valueNode = publisherElement.getElementsByTagName("Value").item(0);
                                    
                            //         if (valueNode != null) {
                            //             String publisherValue = valueNode.getTextContent().trim();
                                        
                            //             if (!publisherValue.isEmpty()) {
                            //                 itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "publisher", null, "ru", publisherValue);
                            //                 log.info("Added publisher: " + publisherValue);
                            //             }
                            //         }
                            //     }
                            // } catch (Exception e) {
                            //     log.warn("Error while processing Publisher field: " + e.getMessage());
                            // }

                            // try {
                            //     NodeList typeNodes = record.getElementsByTagName("Type");
                            //     for (int t = 0; t < typeNodes.getLength(); t++) {
                            //         Element typeElement = (Element) typeNodes.item(t);
                            //         Node qualifierNode = typeElement.getElementsByTagName("Qualifier").item(0);
                            //         Node valueNode = typeElement.getElementsByTagName("Value").item(0);
                                    
                            //         if (valueNode != null) {
                            //             String typeValue = valueNode.getTextContent().trim();
                                        
                            //             if (!typeValue.isEmpty()) {
                            //                 itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "type", null, "ru", typeValue);
                            //                 log.info("Added type: " + typeValue);
                            //             }
                            //         }
                            //     }
                            // } catch (Exception e) {
                            //     log.warn("Error while processing Type field: " + e.getMessage());
                            // }

                            // try {
                            //     NodeList sourceNodes = record.getElementsByTagName("Source");
                            //     for (int s = 0; s < sourceNodes.getLength(); s++) {
                            //         Element sourceElement = (Element) sourceNodes.item(s);
                            //         Node qualifierNode = sourceElement.getElementsByTagName("Qualifier").item(0);
                            //         Node valueNode = sourceElement.getElementsByTagName("Value").item(0);
                                    
                            //         if (valueNode != null) {
                            //             String sourceValue = valueNode.getTextContent().trim();
                                        
                            //             if (!sourceValue.isEmpty()) {
                            //                 itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "source", null, "ru", sourceValue);
                            //                 log.info("Added source: " + sourceValue);
                            //             }
                            //         }
                            //     }
                            // } catch (Exception e) {
                            //     log.warn("Error while processing Source field: " + e.getMessage());
                            // }

                            // try {
                            //     NodeList rightsNodes = record.getElementsByTagName("Rights");
                            //     for (int r = 0; r < rightsNodes.getLength(); r++) {
                            //         Element rightsElement = (Element) rightsNodes.item(r);
                            //         Node qualifierNode = rightsElement.getElementsByTagName("Qualifier").item(0);
                            //         Node valueNode = rightsElement.getElementsByTagName("Value").item(0);
                                    
                            //         if (valueNode != null) {
                            //             String rightsValue = valueNode.getTextContent().trim();
                                        
                            //             // Добавляем только если значение не пустое
                            //             if (!rightsValue.isEmpty()) {
                            //                 itemItem.addMetadata(MetadataSchema.DC_SCHEMA, "rights", null, "ru", rightsValue);
                            //                 log.info("Added rights: " + rightsValue);
                            //             }
                            //         }
                            //     }
                            // } catch (Exception e) {
                            //     log.warn("Error while processing Rights field: " + e.getMessage());
                            // }

                            try {
                                NodeList formats = record.getElementsByTagName("Format");
                                //log.info("doDSPost>>received Format is " + formats);
                                writeMetaDataToItemNormalized(itemItem, "format", record.getElementsByTagName("Format"));

                                formats = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList languages = record.getElementsByTagName("Language");
                                //log.info("doDSPost>>received Language is " + languages);
                                writeMetaDataToItemNormalized(itemItem, "language", record.getElementsByTagName("Language"));

                                languages = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList relations = record.getElementsByTagName("Relation");
                                //log.info("doDSPost>>received Relation is " + relations);
                                writeMetaDataToItemNormalized(itemItem, "relation", record.getElementsByTagName("Relation"));

                                
                                relations = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList coverages = record.getElementsByTagName("Coverage");
                                //log.info("doDSPost>>received Coverage is " + coverages);
                                writeMetaDataToItemNormalized(itemItem, "coverage", record.getElementsByTagName("Coverage"));

                                coverages = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            try {
                                NodeList citation = record.getElementsByTagName("Citation");
                                //log.info("doDSPost>>received Citation is " + citation);
                                writeMetaDataToItemNormalized(itemItem, "citation", record.getElementsByTagName("Citation"));

                                citation = null;
                            } catch (Exception e) {
                                log.info(e.getMessage());
                            }

                            DateFormat df = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
                            Date today = Calendar.getInstance().getTime();
                            String dateNow = df.format(today);
                            log.info("doDSPost>>df&today&dateNow was created");

                            itemItem.setDiscoverable(true);

                            try {
                                NodeList linkNodes = record.getElementsByTagName("Link");
                                if (linkNodes.getLength() > 0) {
                                    Element linkElement = (Element) linkNodes.item(0);
                                    String firstUrl = "http://lib.ssau.ru/download?fname=";
                                    String linkValue = null;

                                    // Получаем значение из элемента Value
                                    Node valueNode = linkElement.getElementsByTagName("Value").item(0);
                                    if (valueNode != null) {
                                        linkValue = valueNode.getTextContent().trim();
                                    }
                                    
                                    // Если в Value ничего нет, пробуем получить текст самого элемента Link
                                    if (linkValue == null || linkValue.isEmpty()) {
                                        linkValue = linkElement.getTextContent().trim();
                                    }

                                    if (linkValue != null && !linkValue.isEmpty()) {
                                        // Кодируем для запроса
                                        String linkEncode = URLEncoder.encode(linkValue, "UTF-8");
                                        // Достаём имя файла
                                        String filenamelel = linkValue.substring(linkValue.lastIndexOf('\\') + 1);
                                        String fileUrl = firstUrl + linkEncode;
                                        log.info("Downloading PDF: " + fileUrl);
                                        
                                        itemItem.addMetadata("dc", "textpart", null, null, "");

                                        // Поток для сохранения файла в DSpace
                                        try (InputStream iss = new URL(fileUrl).openStream()) {

                                            if (!exists) {
                                                itemItem.createBundle("ORIGINAL");
                                                Bitstream b = itemItem.getBundles("ORIGINAL")[0].createBitstream(iss);
                                                b.setName(filenamelel);
                                                b.setDescription("from 1C");
                                                b.setSource("1C");
                                                itemItem.getBundles("ORIGINAL")[0].setPrimaryBitstreamID(b.getID());
                                                BitstreamFormat bf = FormatIdentifier.guessFormat(context, b);
                                                b.setFormat(bf);
                                                b.update();
                                            }
                                            itemItem.update();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Error while attaching PDF", e);
                            }

                            if (exists == false) {
                                log.error("OK I GOT HERE");
                                HandleManager.createHandle(context, itemItem);
                                Metadatum[] dcorevalues2 = itemItem.getMetadata("dc", "identifier", null,
                                        Item.ANY);

                                Metadatum tit = dcorevalues2[0];
                                log.info("doDSPost>>Identifier metadatum received ant Metadatum tit is not caused the exception");
                                try {
                                    SoapHelper sh = new SoapHelper();
                                    sh.writeLink(tit.value, HandleManager.getCanonicalForm(itemItem.getHandle()));
                                } catch (Exception ex) {
                                    log.error("error occured in process of writeLink to webService : " + ex.getMessage());
                                    log.info("error occured in process of writeLink to webService : " + ex.getMessage());
                                }

                                TableRow row = DatabaseManager.row("collection2item");
                                PreparedStatement statement = null;
                                statement = context.getDBConnection().prepareStatement("DELETE FROM workspaceitem WHERE workspace_item_id=" + wsitem.getID());
                                int ij = statement.executeUpdate();
                                itemItem.inheritCollectionDefaultPolicies(col);
                                itemItem.setArchived(true);
                                log.info("doDSPost>>Deletion executed and setArchived");

                            } else {
                                links.add(HandleManager.getCanonicalForm(itemItem.getHandle()));
                            }
                            //request.setAttribute("updatedLinks", links);

                            if (exists == false) {

                                if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow")) {
                                    log.info("doDSPost>>workflow.framework property is equal to xmlworkflow");
                                    try {
                                        XmlWorkflowManager.start(context, wsitem);
                                    } catch (Exception e) {
                                        log.error(LogManager.getHeader(context, "Error while starting xml workflow", "Item id: "), e);
                                        throw new ServletException(e);
                                    }
                                } else {
                                    WorkflowManager.start(context, wsitem);
                                }
                            }

                            request.setAttribute("link", HandleManager.getCanonicalForm(col.getHandle()));
                            itemItem.update();
                            context.commit();

                            //break;
                        } catch (Exception e) {
                            log.error("omg error 1", e);
                        } finally {
                            //moved to finally, cause without it page show system-error
                            log.info("");
                            request.setAttribute("updatedLinks", links);
                        }

                    }

                } catch (ParserConfigurationException e) {
                    log.error("omg error 2", e);
                } catch (SAXException e) {
                    log.error("omg error 3", e);
                } catch (Exception e) {
                    log.error("omg error 4", e);
                }
            }
        }

    } else {
    }

    for (File file : directoryListing) {
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            FileDeleteStrategy.FORCE.delete(file);
        } catch (Exception e) {
            log.info("doDSPost>>error occurred when FORCE.delete(file)");
        }
    }
    context.complete();
    if (howManyWasSubmited > 0) {
        //log.info("ImportMassServlet>>doDSPost>>redirect to mass-import-done");
        request.getRequestDispatcher("/import/mass-import-done.jsp").forward(request, response);
    } else {
        //log.info("ImportMassServlet>>doDSPost>>redirect to mass-import-wrong");
        request.getRequestDispatcher("/import/mass-import-wrong.jsp").forward(request, response);
    }
}

    public void writeMetaDataToItem(Item item, String qualifier, NodeList nodes) {
        for (int j = 0; j < nodes.getLength(); j++) {
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent(), "ru", textSubject.getTextContent());
        }
    }

    public void writeMetaDataToItemLowerCase(Item item, String qualifier, NodeList nodes) {
        for (int j = 0; j < nodes.getLength(); j++) {
            Element subjectNode = (Element) nodes.item(j);

            Node valueNode = subjectNode.getElementsByTagName("Value").item(0);
            Node qualifierNode = subjectNode.getElementsByTagName("Qualifier").item(0);

            if (valueNode == null || qualifierNode == null) {
                continue;
            }

            String valueText = valueNode.getTextContent();
            String qualifierText = qualifierNode.getTextContent().toLowerCase();

            // FOR FIELDS LIKE ds.title.title => dc.title в самом 
            String finalQualifier = qualifierText.equals(qualifier.toLowerCase()) ? null : qualifierText;

            item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, finalQualifier, "ru", valueText);
        }
    }

    // public void writeMetaDataToItemLowerCaseSubject(Item item, String qualifier, NodeList nodes) {
    //     for (int j = 0; j < nodes.getLength(); j++) {
    //         Element subjectNode = (Element) nodes.item(j);
    //         Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
    //         Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
    //         if (qulSubject.getTextContent().toLowerCase().equals("subject")) {
    //             item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, null, "ru", textSubject.getTextContent());
    //         } else {
    //             item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());
    //         }
    //     }
    // }

    public void writeMetaDataToItemLowerCaseIdentifier(Item item, String qualifier, NodeList nodes) {
        for (int j = 0; j < nodes.getLength(); j++) {
            Element subjectNode = (Element) nodes.item(j);
            Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
            Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
            if (qulSubject.getTextContent().toLowerCase().equals("identifier")) {
                item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, null, "ru", textSubject.getTextContent());
                try {
                    SoapHelper sh = new SoapHelper();
                    sh.writeLink(textSubject.getTextContent(), HandleManager.getCanonicalForm(item.getHandle()));
                } catch (Exception ex) {
                    log.error("error occured in process of writeLink to webService : " + ex.getMessage());
                    log.info("error occured in process of writeLink to webService : " + ex.getMessage());
                }
            } else {
                if (qulSubject.getTextContent().toLowerCase().equals("doi")) {
                    item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, "uri", "ru", textSubject.getTextContent());
                } else {
                    item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, qulSubject.getTextContent().toLowerCase(), "ru", textSubject.getTextContent());
                }
            }
        }
    }

    public void writeMetaDataToItemNormalized(Item item, String element, NodeList nodes) {
    for (int j = 0; j < nodes.getLength(); j++) {
        Element el = (Element) nodes.item(j);

        Node valueNode = el.getElementsByTagName("Value").item(0);
        Node qualNode = el.getElementsByTagName("Qualifier").item(0);

        if (valueNode == null || qualNode == null) continue;

        String value = valueNode.getTextContent().trim();
        if (value.isEmpty()) continue;

        String qual = qualNode.getTextContent().trim().toLowerCase();

        // если qualifier = element → не пишем его вторично
        if (qual.equals(element.toLowerCase())) {
            qual = null;
        }

        item.addMetadata(
            MetadataSchema.DC_SCHEMA,
            element.toLowerCase(),
            qual,
            "ru",
            value
        );
    }
}


    // public void writeMetaDataToItemLowerCaseTitle(Item item, String qualifier, NodeList nodes) {
    //     log.info("InportMassServlet>>writeMetaDataToItemLowerCaseTitle was called");
    //     for (int j = 0; j < nodes.getLength(); j++) {
    //         Element subjectNode = (Element) nodes.item(j);
    //         Node textSubject = subjectNode.getElementsByTagName("Value").item(0);
    //         Node qulSubject = subjectNode.getElementsByTagName("Qualifier").item(0);
    //         //log.info("ImportMassServlet>>writeMetaDataToItemLowerCaseTitle>>Received textSubject:" + textSubject.getTextContent());
    //         //log.info("ImportMassServlet>>writeMetaDataToItemLowerCaseTitle>>Received qulSubject:" + qulSubject.getTextContent());
    //         String normalizedQualifier = null;
    //         if (qulSubject != null && qulSubject.getTextContent() != null) {
    //             String q = qulSubject.getTextContent().trim().toLowerCase();
    //             if (!q.isEmpty()) {
    //                 normalizedQualifier = q;
    //                 log.info("InportMassServlet>>writeMetaDataToItemLowerCaseTitle>>normalizedQualifier is " + normalizedQualifier);
    //             }
    //         }

    //         if ("title".equals(normalizedQualifier)) {
    //             log.info("InportMassServlet>>writeMetaDataToItemLowerCaseTitle>>call with null");
    //             item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, null, "ru", textSubject.getTextContent());
    //         } else {
    //             log.info("InportMassServlet>>writeMetaDataToItemLowerCaseTitle>>call with normalizedQ");
    //             item.addMetadata(MetadataSchema.DC_SCHEMA, qualifier, normalizedQualifier, "ru", textSubject.getTextContent());
    //         }
    //     }
    // }

    private static String removeUTF8BOM(String s) {
        if (s.startsWith(UTF8_BOM)) {
            s = s.substring(1);
        }
        return s;
    }

}

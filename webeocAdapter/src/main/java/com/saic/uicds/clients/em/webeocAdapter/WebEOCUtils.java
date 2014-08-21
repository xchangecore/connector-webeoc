package com.saic.uicds.clients.em.webeocAdapter;

import gov.niem.niem.niemCore.x20.ActivityType;
import gov.niem.niem.niemCore.x20.IdentificationType;
import gov.niem.niem.niemCore.x20.TextType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uicds.incident.UICDSIncidentType;

import com.saic.uicds.clients.util.Common;

public class WebEOCUtils {

    private static final String UICDS_ID_ATTRIBUTE_NAME = "uicdsid";
    // private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static Logger logger = LoggerFactory.getLogger("test");

    // for cbp board, use dataid
    private static final String UICDS_DATAID_ATTRIBUTE_NAME = "dataid";
    
        
    /**
     * Returns an array of XmlObjects containing an XmlObject for each data record contained in the
     * input WebEOC data string.
     * 
     * @param data
     * @return
     * @throws XmlException
     */
    public static XmlObject[] getRecordsFromData(String data) throws XmlException {

        XmlObject dataXML = XmlObject.Factory.parse(data);

        XmlObject[] records = WebEOCUtils.getRecordsFromData(dataXML);
        dataXML = null;
        return records;
    }

    /**
     * Returns an array of XmlObjects containing an XmlObject for each data record contained in the
     * input WebEOC data XmlObject.
     * 
     * @param dataObj
     * @return
     */
    public static XmlObject[] getRecordsFromData(XmlObject dataObj) {

        XmlObject[] records = {};
        XmlObject[] dataElements = dataObj.selectChildren(null, "data");
        if (dataElements.length == 1) {

            // for testing, use this call, for real, depend on the cases, FLi 02/29/2012
            // records = dataElements[0].selectChildren(null, "listitems");

            // if use this call, the CBPBoard case record returns null, so use the above call which
            // work for both cases.
            records = dataElements[0].selectChildren(null, "record");
            return records;
        }
        return records;
    }

    public static XmlObject[] getRecordsFromDataEx(String data) throws XmlException {

        XmlObject dataXML = XmlObject.Factory.parse(data);

        XmlObject[] records = WebEOCUtils.getRecordsFromDataEx(dataXML);
        dataXML = null;
        return records;
    }

    public static XmlObject[] getRecordsFromDataEx(XmlObject dataObj) {

        XmlObject[] records = {};
        XmlObject[] dataElements = dataObj.selectChildren(null, "data");
        if (dataElements.length == 1) {

            //for testing, use this call, for real, depend on the cases, FLi 02/29/2012
        	//records = dataElements[0].selectChildren(null, "listitems");
        	
        	//if use this call, the CBPBoard case record returns null, so use the above call which work for both cases.
        	records = dataElements[0].selectChildren(null, "record");

            return records;
        }
        return records;
    }

    /**
     * Extracts the string representing the UICDS incident identifier (Interest Group ID) from the
     * input WebEOC data record.
     * 
     * @param record XmlObject of the WebEOC data record
     * @return
     */
    public static String getUicdsIDFromRecord(XmlObject record) {

        XmlObject igid = record.selectAttribute(null, UICDS_ID_ATTRIBUTE_NAME);
        if (igid != null) {
            return Common.getTextFromAny(igid);
        }
        return null;
    }

    public static String getDataIDFromRecord(XmlObject record) {

        XmlObject igid = record.selectAttribute(null, UICDS_DATAID_ATTRIBUTE_NAME);
        if (igid != null) {
            return Common.getTextFromAny(igid);
        }
        return null;
    }

    public static Date getDateFromWebEOCEntrydataField(String dateTimeString) throws ParseException {

        Date dateTime = null;
        if (dateTimeString != null && dateTimeString.length() > 0) {
            SimpleDateFormat ISO8601Local = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            TimeZone timeZone = TimeZone.getDefault();
            ISO8601Local.setTimeZone(timeZone);
            dateTime = (Date) ISO8601Local.parse(dateTimeString.trim());
        }
        return dateTime;
    }

    public static List<String> getFieldsFromRecord(XmlObject record) {

        ArrayList<String> list = new ArrayList<String>();

        XmlCursor cursor = record.newCursor();
        cursor.toNextToken();

        while (cursor.toNextAttribute()) {
            list.add(cursor.getName().toString());
        }

        cursor.dispose();

        return list;
    }

    /**
     * Gets a value from a record for the input attribute (field) name.
     * 
     * @param attributeName
     * @param record
     * @return
     */
    public static String getAttributeFromRecord(String attributeName, XmlObject record) {

        XmlObject valueXML = record.selectAttribute(null, attributeName);
        if (valueXML != null) {
            return Common.getTextFromAny(valueXML);
        }
        return "";
    }

    public static HashMap<String, String> getFieldMapFromRecord(XmlObject record) {

        HashMap<String, String> map = new HashMap<String, String>();

        List<String> attributes = getFieldsFromRecord(record);

        for (String attribute : attributes) {
            map.put(attribute, getAttributeFromRecord(attribute, record));
        }

        return map;
    }

    public static UICDSIncidentType addIncidentEvent(UICDSIncidentType incident, String reason,
        XmlObject record, Board board, String serverURL) {

        String dataid = WebEOCUtils.getAttributeFromRecord(Board.DATAID_FIELD, record);
        String boardname = board.getBoardName();

        return addIncidentEvent(incident, reason, Constants.WEBEOC_OWNER_NAME, dataid, boardname,
            serverURL);
    }
    
    public static UICDSIncidentType addIncidentEvent(UICDSIncidentType incident, String reason,
            String category, String dataid, String boardname, String serverURL) {

            return Common.addIncidentEvent(incident, reason, category, dataid, serverURL + "#"
                + boardname);
        }
      
    
    public static UICDSIncidentType addIncidentEventMema(UICDSIncidentType incident, String reason,
            XmlObject record, Board board, String serverURL, String incidentName) {

            String dataid = WebEOCUtils.getAttributeFromRecord(Board.DATAID_FIELD, record);
            String boardname = board.getBoardName();

            return addIncidentEventMema2(incident, reason, Constants.WEBEOC_OWNER_NAME, dataid, boardname,
                serverURL, incidentName);
            
        }
    
     public static UICDSIncidentType addIncidentEventMema2(UICDSIncidentType incident, String reason,
            String category, String dataid, String boardname, String serverURL, String incidentName) {

            return Common.addIncidentEventMema(incident, reason, category, dataid, serverURL + "#"
                + boardname, incidentName);
     }
	
    public static String getDataIdFromIncidentEvent(UICDSIncidentType incident) {

        if (incident.sizeOfIncidentEventArray() > 0) {
            for (ActivityType event : incident.getIncidentEventArray()) {
                if (event.sizeOfActivityCategoryTextArray() > 0) {
                    if (event.getActivityCategoryTextArray(0).getStringValue().equals(
                        Constants.WEBEOC_OWNER_NAME)) {
                        return event.getActivityIdentificationArray(0).getIdentificationIDArray(0).getStringValue();
                    }
                }
            }
        }
        return null;
    }

    public static boolean createdByWebEOCAdapter(UICDSIncidentType incident) {

        boolean foundWEBEOC = false;
        boolean foundCREATED = false;

        if (incident != null && incident.sizeOfIncidentEventArray() > 0) {
            for (ActivityType event : incident.getIncidentEventArray()) {
                if (event.sizeOfActivityIdentificationArray() > 0) {
                    for (gov.niem.niem.niemCore.x20.IdentificationType id : event.getActivityIdentificationArray()) {
                        if (id.sizeOfIdentificationCategoryDescriptionTextArray() > 0) {
                            for (TextType desc : id.getIdentificationCategoryDescriptionTextArray()) {
                                if (desc.getStringValue().equalsIgnoreCase(
                                    Constants.WEBEOC_OWNER_NAME)) {
                                    foundWEBEOC = true;
                                }
                            }
                        }
                    }
                }
                if (event.sizeOfActivityReasonTextArray() > 0) {
                    for (TextType reason : event.getActivityReasonTextArray()) {
                        if (reason.getStringValue().equalsIgnoreCase(
                            Constants.WEBEOC_CREATED_REASON)) {
                            foundCREATED = true;
                        }
                    }
                }
            }
        }

        return foundWEBEOC && foundCREATED;
    }

    public static String createXmlDataFromMap(Map<String, String> map) {

        XmlObject obj = XmlObject.Factory.newInstance();
        XmlCursor cursor = obj.newCursor();

        cursor.toNextToken();
        cursor.beginElement("data");
        for (String boardFieldName : map.keySet()) {
            cursor.insertElementWithText(boardFieldName, map.get(boardFieldName));
        }

        cursor.dispose();

        return obj.xmlText();
    }

    public static String createWebEOCRecordFromMap(String boardname, Map<String, String> map) {

        XmlObject obj = XmlObject.Factory.newInstance();
        XmlCursor cursor = obj.newCursor();

        cursor.toNextToken();
        cursor.beginElement("record");
        cursor.insertAttributeWithValue(Board.TABLENAME_FIELD, boardname);
        for (String boardFieldName : map.keySet()) {
            cursor.insertAttributeWithValue(boardFieldName, map.get(boardFieldName));
        }

        cursor.dispose();

        return obj.xmlText();
    }

    public static int findWebEOCDataID(UICDSIncidentType incident, String boardname) {

        boolean foundWEBEOC = false;
        boolean foundRECEIVED = false;

        if (incident != null && incident.sizeOfIncidentEventArray() > 0) {
            for (ActivityType event : incident.getIncidentEventArray()) {
                if (event.sizeOfActivityCategoryTextArray() > 0) {
                    for (TextType category : event.getActivityCategoryTextArray()) {
                        if (category.getStringValue().equalsIgnoreCase(Constants.WEBEOC_OWNER_NAME)) {
                            foundWEBEOC = true;
                        }
                    }
                }
                if (event.sizeOfActivityReasonTextArray() > 0) {
                    for (TextType reason : event.getActivityReasonTextArray()) {
                        if (reason.getStringValue().equalsIgnoreCase(
                            Constants.WEBEOC_RECEIVED_REASON)) {
                            foundRECEIVED = true;
                        }
                    }
                }
                if (foundWEBEOC && foundRECEIVED) {
                    if (event.sizeOfActivityIdentificationArray() > 0) {
                        for (IdentificationType identification : event.getActivityIdentificationArray()) {
                            if (identification.sizeOfIdentificationCategoryDescriptionTextArray() > 0) {
                                if (identification.getIdentificationCategoryDescriptionTextArray(0).getStringValue().equalsIgnoreCase(
                                    boardname)) {
                                    if (identification.sizeOfIdentificationIDArray() > 0) {
                                        try {
                                            int dataid = Integer.parseInt(identification.getIdentificationIDArray(
                                                0).getStringValue());
                                            return dataid;
                                        } catch (NumberFormatException e) {
                                            return 0;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return 0;
    }

    public static boolean lastUpdatedByWebEOCAdapter(XmlObject record, String adapterPosition) {

        String position = WebEOCUtils.getAttributeFromRecord(UICDSIncidentsBoard.POSITION_FIELD,
            record);
        return position.equals(adapterPosition);
    }


    //===========to support dataID with incidentName, FLI on 04/26/2012 =================
    private static Map<String,String> dataIDIncidentMap=null; 
    
    public static void createDataIDIncidentMap()
    {
    	if(dataIDIncidentMap==null)
    	{
    		dataIDIncidentMap = new HashMap<String, String>();
    	}
    }
    
    public static Map<String,String> getDataIDIncidentMap()
    {
    	return dataIDIncidentMap;
    }
    
    public static void addPairsToDataIDInicentMap(String dataID, String IncidentName)
    {
    	dataIDIncidentMap.put(dataID, IncidentName);
    }
    
    public static void cleanUpDataIDInicdentMap()
    {
    	dataIDIncidentMap.clear();
    }
    
    public static String findIncidentNameByDataID(String dataID)
    {
    	
    	if(dataIDIncidentMap !=null)
    	{
	    	//Get Map in Set interface to get key and value
	        Set s=dataIDIncidentMap.entrySet();
	
	        //Move next key and value of Map by iterator
	        Iterator it=s.iterator();
	
	        while(it.hasNext())
	        {
	            // key=value separator this by Map.Entry to get key and value
	        	Map.Entry m =(Map.Entry)it.next();
	
	            // getKey is used to get key of Map
	            String key=(String)m.getKey();
	
	            // getValue is used to get value of key in Map
	            String value=(String)m.getValue();
	            
	            if(key.equalsIgnoreCase(dataID))
	            {
	            	return value;
	            }
	
	          //  System.out.println("Key :"+key+"  Value :"+value);
	        }
    	}
        return null;
    }
    
  //End of block======================================================================
}

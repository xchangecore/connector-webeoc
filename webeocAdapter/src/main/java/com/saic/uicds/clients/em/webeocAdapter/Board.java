package com.saic.uicds.clients.em.webeocAdapter;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import x0.comEsi911Webeoc7Api1.ArrayOfString;
import x0.comEsi911Webeoc7Api1.GetDataResponseDocument;

public class Board {

    public static final String ENTRYDATE_FIELD = "entrydate";

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String DATAID_FIELD = "dataid";   
    public static final String TABLENAME_FIELD = "tablename";
    public static final String POSITION_FIELD = "positionname";

    private String boardName;
    private String viewName;
    private String inputViewName;  
    
    private String incidentName;    
        
    protected HashMap<Integer, BoardEntry> items = new HashMap<Integer, BoardEntry>();

    public class BoardEntry {
        private Integer dataid;
        private Date entryDate;
        private String record;
        private String incidentID;

        public BoardEntry(XmlObject record) {

            try {
                setRecord(record);
            } catch (IllegalArgumentException e) {
                logger.error("Cannot create BoardEntry: " + e.getMessage());
            }
        }

        public BoardEntry(XmlObject record, String incidentID) {

            try {
                setRecord(record);
            } catch (IllegalArgumentException e) {
                logger.error("Cannot create BoardEntry: " + e.getMessage());
            }

            this.incidentID = incidentID;
        }

        public void setIncidentID(String incidentID) {

            this.incidentID = incidentID;

        }

        public String getIncidentID() {

            return this.incidentID;

        }

        /**
         * @return the record
         */
        public String getRecord() {

            return record;
        }

        /**
         * @param record the record to set
         */
        public void setRecord(XmlObject record) throws IllegalArgumentException {

            this.record = new String(record.xmlText());
            try {
                this.dataid = Integer.parseInt(WebEOCUtils.getAttributeFromRecord(DATAID_FIELD,
                    record));
            } catch (NumberFormatException e) {
                logger.error("Error parsing dataid field from a board entry");
                this.dataid = 0;
            }
            try {
                entryDate = WebEOCUtils.getDateFromWebEOCEntrydataField(WebEOCUtils.getAttributeFromRecord(
                    ENTRYDATE_FIELD, record));
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        /**
         * @return the dataid
         */
        public Integer getDataid() {

            return dataid;
        }

        /**
         * @return the entryDate
         */
        public Date getEntryDate() {

            return entryDate;
        }
    }

    public void setBoardName(String b) {

        boardName = b;
    }

    public void setViewName(String v) {

        viewName = v;
    }

    public void setInputViewName(String v) {

        inputViewName = v;
    }

   
    public String getBoardName() {

        return boardName;
    }

    public String getViewName() {

        return viewName;
    }

    public String getInputViewName() {

        return inputViewName;
    }

    public void setIncidentName(String i) {

        incidentName = i;
    }
    
    public String getIncidentName() {

        return incidentName;
    }

    public XmlObject getCachedItem(Integer dataid) {

        if (items.containsKey(dataid)) {
            return getBoardEntryXmlObject(dataid);
        }
        return null;
    }

    public BoardEntry getCachedBoardEntry(Integer dataid) {

        if (items.containsKey(dataid)) {
            return items.get(dataid);
        }
        return null;
    }

    public void addCachedItem(XmlObject record) {

        Integer dataid = Integer.parseInt(WebEOCUtils.getAttributeFromRecord(DATAID_FIELD, record));
        items.put(dataid, new BoardEntry(record));
    }

    public void addCachedItem(XmlObject record, String incidentID) {

        Integer dataid = Integer.parseInt(WebEOCUtils.getAttributeFromRecord(DATAID_FIELD, record));
        BoardEntry boardEntry = new BoardEntry(record);
        boardEntry.setIncidentID(incidentID);
        items.put(dataid, boardEntry);

    }

    public XmlObject getBoardEntryXmlObject(Integer dataid) {

        try {
            if (items.containsKey(dataid)) {
                XmlObject obj = XmlObject.Factory.parse(items.get(dataid).getRecord());
                return obj;
            }
        } catch (XmlException e) {
            logger.error("Error parsing a cached board entry");
        }
        return null;
    }

    public XmlObject[] getBoardEntryXmlObjectByFieldValue(String testFieldName,
        String testFieldValue) {

        ArrayList<XmlObject> records = new ArrayList<XmlObject>();
        for (Integer dataid : items.keySet()) {
            try {
                XmlObject obj = XmlObject.Factory.parse(items.get(dataid).getRecord());
                if (testFieldValue.equalsIgnoreCase(WebEOCUtils.getAttributeFromRecord(
                    testFieldName, obj))) {
                    records.add(obj);
                }
            } catch (XmlException e) {
                logger.error("Error parsing an record in the items map: " + e.getMessage());
            }
        }
        XmlObject[] ret = new XmlObject[records.size()];
        records.toArray(ret);
        return ret;
    }

    public int getCacheSize() {

        return items.size();
    }

    public ArrayList<XmlObject> getListOfUpdatedItems(String data) {

        ArrayList<XmlObject> updatedItems = new ArrayList<XmlObject>();

        try {
            XmlObject[] records = WebEOCUtils.getRecordsFromData(data);

            for (XmlObject record : records) {
                // Don't process if there is a parsing exception
                try {
                    if (isNewOrUpdatedRecord(record)) {
                        updatedItems.add(record);
                    }
                } catch (IllegalArgumentException e) {
                    logger.error(e.getMessage());
                }
            }
            records = null;

        } catch (XmlException e) {
            logger.error("Error parsing data from WebEOC");
            logger.debug("   DATA: " + data);
        }

        return updatedItems;
    }

    
    public ArrayList<XmlObject> getListOfUpdatedItemsEX(WebEOCWebServiceClient client, String data) 
    {
    	 ArrayList<XmlObject> updatedItems = new ArrayList<XmlObject>();
    	 ArrayOfString list = client.getIncidentList();
    	 
    	 //prepare the dataid and incident map
    	 WebEOCUtils.createDataIDIncidentMap();
     	 WebEOCUtils.cleanUpDataIDInicdentMap();
     	
         for (int i = 0; i < list.sizeOfStringArray(); i++) 
         {
        	 String incidentName =  list.getStringArray(i);
        	        	 
        	 //take too long for testing, just check a few, removed after done the test.
        	 if(incidentName.equalsIgnoreCase("UICDS Test 1")
        			 || incidentName.equalsIgnoreCase("UICDS Test 3")
        			 || incidentName.equalsIgnoreCase("UICDS Demo")
        			 )
        	 {
	        	 setIncidentName(incidentName);
	        	 System.out.println("Processing incident " + incidentName + "....."); 
			     GetDataResponseDocument response = client.getDataFromBoard(this);
			     
			        XmlObject[] records;
			        try {
			            records = WebEOCUtils.getRecordsFromData(response.getGetDataResponse().getGetDataResult());
			            
			         if (records.length > 0) 
			         {
			        	 System.out.println("got " + Integer.toString(records.length) + " record/records from " + incidentName);  
			             	  
			            for (XmlObject record : records) 
			            {
			                      	             
			                try {
				                    if (isNewOrUpdatedRecord(record)) 
				                    {
				                    	//add this record for processing
				                        updatedItems.add(record);
				                        
				                        //add dataid and incident into the dataid and incident map for future using
				                        String dataidStr = WebEOCUtils.getAttributeFromRecord(Board.DATAID_FIELD, record);           
								        WebEOCUtils.addPairsToDataIDInicentMap(dataidStr, incidentName);
								        System.out.println("Added data id " +dataidStr + " and incident name "+ incidentName +" to dataid and incident name map");
				                    }
			                } catch (IllegalArgumentException e) {
			                    logger.error(e.getMessage());
			                }
			            }
			         }
			         else
			         {
			        	 System.out.println("No records found from " + incidentName);   
			         }
			            records = null;
			        }
			        catch (XmlException e) {
			        	logger.error("Error parsing data from WebEOC");
			            logger.debug("   DATA: " + data);
		            }
        	 }
        	 
         }
         
         return updatedItems;
        
    }
    
    
    
    
    private boolean isNewOrUpdatedRecord(XmlObject record) throws IllegalArgumentException {

        try {
            Integer dataid = Integer.parseInt(WebEOCUtils.getAttributeFromRecord(DATAID_FIELD,
                record));
            if (isNotInCacheOrIsUpdated(dataid, record)) {
                if (items.containsKey(dataid)) {
                    items.put(dataid, new BoardEntry(record, items.get(dataid).incidentID));
                } else {
                    items.put(dataid, new BoardEntry(record));
                }
                record = null;
                return true;
            }
            return false;

        } catch (NumberFormatException e) {
            logger.error("Error parsing dataid: "
                + WebEOCUtils.getAttributeFromRecord(DATAID_FIELD, record));
            return false;
        }
    }

    private boolean isNotInCacheOrIsUpdated(Integer dataid, XmlObject record)
        throws IllegalArgumentException {

        if (!items.containsKey(dataid)) {
            return true;
        }

        Date currentEventDate = items.get(dataid).getEntryDate();
        String dateTimeString = WebEOCUtils.getAttributeFromRecord(ENTRYDATE_FIELD, record);
        Date newEventDate;
        try {
            newEventDate = WebEOCUtils.getDateFromWebEOCEntrydataField(dateTimeString);
            if (currentEventDate != null && newEventDate != null
                && newEventDate.after(currentEventDate)) {
                return true;
            } else {
                return false;
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("Error parsing date string (" + dateTimeString
                + ") should be yyyy-MM-dd'T'HH:mm:ss format: " + e.getMessage());
        }
    }

}

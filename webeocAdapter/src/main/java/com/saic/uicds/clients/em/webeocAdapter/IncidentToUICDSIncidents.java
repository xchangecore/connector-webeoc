/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

import gov.niem.niem.niemCore.x20.AddressFullTextDocument;


import gov.niem.niem.niemCore.x20.AddressType;
import gov.niem.niem.niemCore.x20.AreaType;
import gov.niem.niem.niemCore.x20.OrganizationType;
import gov.niem.niem.niemCore.x20.TwoDimensionalGeographicCoordinateType;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uicds.incident.IncidentDocument;
import org.uicds.notificationService.WorkProductDeletedNotificationType;
import org.uicds.workProductService.WorkProductPublicationResponseType;

import x0.comEsi911Webeoc7Api1.GetDataByDataIdResponseDocument;
import x0.comEsi911Webeoc7Api1.GetFilteredDataResponseDocument;
import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;

import com.saic.precis.x2009.x06.base.IdentificationType;
import com.saic.precis.x2009.x06.base.IdentifierType;
import com.saic.precis.x2009.x06.base.PropertiesType;
import com.saic.precis.x2009.x06.base.StateType;
import com.saic.precis.x2009.x06.structures.WorkProductDocument.WorkProduct;
import com.saic.uicds.clients.em.async.UicdsCore;
import com.saic.uicds.clients.em.async.UicdsIncident;
import com.saic.uicds.clients.em.async.UicdsWorkProduct;
import com.saic.uicds.clients.em.async.WorkProductListener;
import com.saic.uicds.clients.util.Common;

import org.jsoup.*;
import org.jsoup.nodes.Document;

/**
 * Create a WorkProductListener that will create an entry on the UICDS Incidents Board when a new
 * UICDS incident is created.
 * 
 * @author roger
 * 
 */
public class IncidentToUICDSIncidents
    implements WorkProductListener {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private UicdsCore uicdsCore;

    private WebEOCWebServiceClient webEocClient;

    private UICDSIncidentsBoard uicdsIncidentsBoard;

    private ArrayList<WorkProduct> workProductQueue = new ArrayList<WorkProduct>();

    private boolean initializing;

    public UicdsCore getUicdsCore() {

        return uicdsCore;
    }

    public void setUicdsCore(UicdsCore uicdsCore) {

        this.uicdsCore = uicdsCore;
    }

    public WebEOCWebServiceClient getWebEocClient() {

        return webEocClient;
    }

    public void setWebEocClient(WebEOCWebServiceClient webEocClient) {

        this.webEocClient = webEocClient;
    }

    /**
     * @return the uicdsIncidentsBoard
     */
    public UICDSIncidentsBoard getuicdsIncidentsBoard() {

        return uicdsIncidentsBoard;
    }

    /**
     * @param uicdsIncidentsBoard the uicdsIncidentsBoard to set
     */
    public void setuicdsIncidentsBoard(UICDSIncidentsBoard uicdsIncidentsBoard) {

        this.uicdsIncidentsBoard = uicdsIncidentsBoard;
    }

    public int sizeOfQueuedWorkProducts() {

        return workProductQueue.size();
    }

    public void initialize() {

        initializing = true;

        // Register to receive callback for UICDS notifications
        uicdsCore.registerListener(this);

        initializing = false;
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.async.WorkProductListener#handleWorkProductUpdate(com.saic.precis.x2009.x06.structures.WorkProductDocument.WorkProduct)
     */
    @Override
    public void handleWorkProductUpdate(WorkProduct workProduct) {

        if (initializing) {
            return;
        }

        // Check if there are any work products that got queued for processing
        if (workProductQueue.size() > 0) {
            ArrayList<WorkProduct> queue = new ArrayList<WorkProduct>(workProductQueue);
            workProductQueue.clear();
            for (WorkProduct wp : queue) {
                processWorkProduct(wp);
            }
        }

        // TODO: Should determine if there was a problem processing the queued data and if there was
        // then just add this to the queue.
        // Process the new work product
        processWorkProduct(workProduct);

    }

    private void processWorkProduct(WorkProduct workProduct) {

        IdentificationType workProductIdentifier = Common.getIdentificationElement(workProduct);
        PropertiesType properties = Common.getPropertiesElement(workProduct);

        // Only handle Incident updates
        if (!workProductIdentifier.getType().getStringValue().equalsIgnoreCase(
            UicdsIncident.INCIDENT_WP_TYPE)) {
            return;
        }

        // Only process this update if this update was NOT posted by this application
        // Otherwise an infinite loop of updates may start.
        if (!properties.getLastUpdatedBy().getStringValue().equals(
            uicdsCore.getFullResourceInstanceID())) {
            try {
                processUpdateFromOtherUICDSClients(workProductIdentifier, properties);
            } catch (WebEOCOperationException e) {
                workProductQueue.add(workProduct);
            }
        }
    }

    private void processUpdateFromOtherUICDSClients(IdentificationType workProductIdentifier,
        PropertiesType properties) throws WebEOCOperationException {

        String interestGroupID = null;
        if (properties.getAssociatedGroups().sizeOfIdentifierArray() > 0) {
            interestGroupID = properties.getAssociatedGroups().getIdentifierArray(0).getStringValue();
            logger.debug("Received UICDS incident notification: " + interestGroupID);
        }

        // Get the full work product
        IncidentDocument incident = getIncidentDocument(workProductIdentifier);

        // Get the associated WebEOC record dataid if it exists
        int webEocDataID = webEOCRecordExistsForIncident(properties);

        // Close the WebEOC entry if the incident document is null (archived already) or empty
        if (incident == null || !incident.getIncident().getDomNode().hasChildNodes()) {
            closeWebEOCRecord(workProductIdentifier, interestGroupID, webEocDataID);
        }
        // Else update the entry
        else {
            updateWebEOCRecord(workProductIdentifier, incident, webEocDataID);
        }
    }

    private void updateWebEOCRecord(IdentificationType workProductIdentifier,
        IncidentDocument incident, int webEocDataID) throws WebEOCOperationException {

        Map<String, String> fieldMap = getFieldMapForIncident(incident,
            workProductIdentifier.getState());
        ;

        // If an entry does not exist for this incident then create one
        if (webEocDataID == 0) {

            if (fieldMap.size() > 0) {
                logger.info("Creating entry on UICDS Incidents board for incident "
                    + workProductIdentifier.getIdentifier().getStringValue());

                // Create a new entry
                createNewWebEOCEntry(workProductIdentifier, incident, fieldMap);
            } else {
                logger.error("no field values to create an incident with");
            }
        }
        // else update the entry
        else {
            if (fieldMap.size() > 0) {
                logger.info("Updating incident with dataid " + webEocDataID);

                // Update a board entry
                webEocClient.updateDataOnBoard(uicdsIncidentsBoard,
                    uicdsIncidentsBoard.getIncidentName(), webEocDataID, fieldMap);
            } else {
                logger.error("no field values to update the incident with");
            }
        }
    }

    public void createNewWebEOCEntry(IdentificationType workProductIdentifier,
        IncidentDocument incident, Map<String, String> fieldMap) throws WebEOCOperationException {

        int dataid = webEocClient.addDataToBoard(uicdsIncidentsBoard,
            uicdsIncidentsBoard.getIncidentName(), fieldMap);

        // Add the new record to the board's cache
        GetDataByDataIdResponseDocument response = webEocClient.getDataByIdFromBoard(
            uicdsIncidentsBoard, dataid);
        try {
            if (response.getGetDataByDataIdResponse() != null
                && response.getGetDataByDataIdResponse().getGetDataByDataIdResult() != null) {
                XmlObject[] records;
                records = WebEOCUtils.getRecordsFromData(response.getGetDataByDataIdResponse().getGetDataByDataIdResult());
                if (records.length != 1) {
                    logger.error("found more that one record for dataid: " + dataid);
                } else {
                    uicdsIncidentsBoard.addCachedItem(records[0]);
                }
            }
        } catch (XmlException e) {
            logger.error("Error parsing new WebEOC record for new incident so not added to local cache: "
                + e.getMessage());
        }
    }

    private void closeWebEOCRecord(IdentificationType workProductIdentifier,
        String interestGroupID, int webEocDataID) throws WebEOCOperationException {

        // Else update with last values and close the WebEOC entry
        // if (workProductIdentifier.getState().equals(StateType.INACTIVE)) {
        if (webEocDataID != 0) {
            Map<String, String> fieldMap = uicdsIncidentsBoard.getFieldMapForCachedIncident(interestGroupID);
            fieldMap.put(UICDSIncidentsBoard.STATUS_FIELD, UICDSIncidentsBoard.CLOSED_STATUS_VALUE);
            if (fieldMap.size() > 1) {
                logger.info("Closing entry " + webEocDataID);
                webEocClient.updateDataOnBoard(uicdsIncidentsBoard,
                    uicdsIncidentsBoard.getIncidentName(), webEocDataID, fieldMap);
            } else {
                logger.error("Could not get field map for incident " + interestGroupID);
            }
        } else {
            logger.error("Could not find matching record from WebEOC when trying to close incident: "
                + interestGroupID);
        }
        // }
        // else {
        // logger.error("Incident data from work product was null for active work product");
        // }
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.async.WorkProductListener#handleWorkProductDelete(org.uicds.notificationService.WorkProductDeletedNotificationType)
     */
    @Override
    public void handleWorkProductDelete(
        WorkProductDeletedNotificationType workProductDeletedNotification) {

        if (initializing) {
            return;
        }
    }

    public HashMap<String, String> getFieldMapForIncident(IncidentDocument incident,
        StateType.Enum state) {

        // Get a map of the fields to post to UICDS Incidents entry
        HashMap<String, String> fieldMap = mapIncidentToIncidentsFields(incident);
        if (state.equals(StateType.ACTIVE)) {
            fieldMap.put(UICDSIncidentsBoard.STATUS_FIELD, UICDSIncidentsBoard.OPEN_STATUS_VALUE);
        } else {
            fieldMap.put(UICDSIncidentsBoard.STATUS_FIELD, UICDSIncidentsBoard.CLOSED_STATUS_VALUE);
        }
        return fieldMap;
    }

    private IncidentDocument getIncidentDocument(IdentificationType workProductIdentifier) {

        WorkProduct workProduct = uicdsCore.getWorkProductFromCore(workProductIdentifier);
        if (workProduct != null) {
            UicdsWorkProduct uicdsWorkProduct = new UicdsWorkProduct(workProduct);
            XmlObject content = uicdsWorkProduct.getContent(UicdsIncident.INCIDENT_SERVICE_NS,
                UicdsIncident.INCIDENT_ELEMENT_NAME);
            if (content != null) {
                try {
                    IncidentDocument incident = IncidentDocument.Factory.parse(content.getDomNode());
                    return incident;
                } catch (XmlException e) {
                    logger.error("Error parsing Incident document");
                    return null;
                }
            }
        }
        return null;
    }

    private int webEOCRecordExistsForIncident(PropertiesType properties)
        throws WebEOCOperationException {

        // Get the data from the board given the interest group ID
        if (properties.getAssociatedGroups().sizeOfIdentifierArray() > 0) {
            IdentifierType igid = properties.getAssociatedGroups().getIdentifierArray(0);

            // Return from value cached on UICDS Incidents board if found
            XmlObject record = uicdsIncidentsBoard.getRecordByInterestID(igid.getStringValue());
            if (record != null) {
                XmlObject igAttribute = record.selectAttribute(null,
                    UICDSIncidentsBoard.DATAID_FIELD);
                if (igAttribute != null) {
                    return Integer.parseInt(Common.getTextFromAny(igAttribute));
                }
            }

            // Go to the board and look for a matching entry
            HashMap<String, String> filters = new HashMap<String, String>();
            filters.put(UICDSIncidentsBoard.UICDS_IG_ID_FIELD, igid.getStringValue());
            
            GetFilteredDataResponseDocument response = webEocClient.getFilteredDataFromBoard(
                uicdsIncidentsBoard, filters);
            String data = response.getGetFilteredDataResponse().getGetFilteredDataResult();

            // no matching data then the incident is not on the UICDS Incidents Board
            if (data == null || data.isEmpty()) {
                return 0;
            } else {
                int dataid = getDataIDForMatchingUicdsIncident(data, igid.getStringValue());

                return dataid;
            }
        }
        // Should never hit this
        return 0;
    }

    private int getDataIDForMatchingUicdsIncident(String data, String interestGroupID) {

        try {
            XmlObject dataObj = XmlObject.Factory.parse(data);
            Integer idataid = getDataIDForIGFromDataObject(interestGroupID, dataObj);
            return idataid;
        } catch (XmlException e) {
            logger.error("Error parsing data return: " + e.getMessage());
            return 0;
        }
    }

    private Integer getDataIDForIGFromDataObject(String interestGroupID, XmlObject dataObj) {

        Integer id = 0;
        Integer idataid = 0;
        if (dataObj != null) {
            XmlObject[] records = WebEOCUtils.getRecordsFromData(dataObj);
            if (records.length > 0) {
                for (XmlObject record : records) {
                    XmlObject igid = record.selectAttribute(null,
                        UICDSIncidentsBoard.UICDS_IG_ID_FIELD);
                    if (igid != null
                        && Common.getTextFromAny(igid).equalsIgnoreCase(interestGroupID)) {
                        XmlObject dataid = record.selectAttribute(null, Board.DATAID_FIELD);
                        if (dataid != null) {
                            idataid = Integer.parseInt(Common.getTextFromAny(dataid));
                            // update the boards cache of record
                            uicdsIncidentsBoard.addCachedItem(record);
                            break;
                        }
                    }
                }
            }
        }
        return idataid;
    }

    private HashMap<String, String> mapIncidentToIncidentsFields(IncidentDocument incident) {

        // build up map of incident values
        HashMap<String, String> fieldValueMap = new HashMap<String, String>();

        if (incident.getIncident() == null) {
            return fieldValueMap;
        }

        // incident name
        if (incident.getIncident().sizeOfActivityNameArray() > 0) {
            fieldValueMap.put(UICDSIncidentsBoard.LABEL_FIELD,
                incident.getIncident().getActivityNameArray(0).getStringValue());
        }

        
        //for debugging 
        String webeocDescriptionStr = incident.getIncident().getActivityDescriptionTextArray(0).getStringValue();
       
        //more work possible
        // Document doc = Jsoup.parse(webeocDescriptionStr);
        
        if(isHtmlString(webeocDescriptionStr))
        {
	       // String realPlainStr=Jsoup.parse(webeocDescriptionStr).text();
	        
             String realPlainStr = Jsoup.parse(webeocDescriptionStr.replaceAll("(?i)<br[^>]*>", "br2nl").replaceAll("\n", "br2nl")).text();
        	 realPlainStr = realPlainStr.replaceAll("br2nl ", "\n").replaceAll("br2nl", "\n").trim();
        	    
	        // incident description
	        if (incident.getIncident().sizeOfActivityDescriptionTextArray() > 0) {
	            fieldValueMap.put(UICDSIncidentsBoard.DESCRIPTION_FIELD, realPlainStr);
	        }
        }
        else
        {
        	 // incident description
	        if (incident.getIncident().sizeOfActivityDescriptionTextArray() > 0) {
	            fieldValueMap.put(UICDSIncidentsBoard.DESCRIPTION_FIELD,  webeocDescriptionStr); 
	            		//incident.getIncident().getActivityDescriptionTextArray(0).getStringValue());
	        }
        }
		
        
        // UICDS incident id
        if (incident.getIncident().sizeOfActivityIdentificationArray() > 0) {
            if (incident.getIncident().getActivityIdentificationArray(0).sizeOfIdentificationIDArray() > 0) {
                fieldValueMap.put(
                    UICDSIncidentsBoard.UICDS_IG_ID_FIELD,
                    incident.getIncident().getActivityIdentificationArray(0).getIdentificationIDArray(
                        0).getStringValue());
            }
        }

        if (incident.getIncident().sizeOfActivityDateRepresentationArray() > 0) {
            XmlCursor cursor = incident.getIncident().getActivityDateRepresentationArray(0).newCursor();
            String calendarString = cursor.getTextValue();
            cursor.dispose();
            if (calendarString != null && calendarString.length() > 0) {
                SimpleDateFormat ISO8601Local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                TimeZone timeZone = TimeZone.getDefault();
                ISO8601Local.setTimeZone(timeZone);
                try {
                    Date dateTime = (Date) ISO8601Local.parse(calendarString.trim());
                    fieldValueMap.put(UICDSIncidentsBoard.EVENT_DATETIME_FIELD,
                        ISO8601Local.format(dateTime));
                } catch (ParseException e) {
                    System.err.println("Error parsing date string should be yyyy-MM-dd'T'HH:mm:ss format: "
                        + e.getMessage());
                }
            }
        }

        // Get the location (address and lat/lon)
        if (incident.getIncident().sizeOfIncidentLocationArray() > 0) {
            // get address
            if (incident.getIncident().getIncidentLocationArray(0).sizeOfLocationAddressArray() > 0) {
                AddressFullTextDocument fullText;
                AddressType address = incident.getIncident().getIncidentLocationArray(0).getLocationAddressArray(
                    0);
                try {
                    fullText = AddressFullTextDocument.Factory.parse(address.xmlText());
                    fieldValueMap.put(UICDSIncidentsBoard.ADDRESS_FIELD,
                        fullText.getAddressFullText().getStringValue());
                    // System.out.println(fullText.getAddressFullText().getStringValue());
                } catch (XmlException e) {
                    System.err.println("Address cannot be parsed");
                    ;
                }
            }

            // get lat/lon
            if (incident.getIncident().getIncidentLocationArray(0).sizeOfLocationAreaArray() > 0) {
                AreaType[] areas = incident.getIncident().getIncidentLocationArray(0).getLocationAreaArray();
                // TODO: figure out what to do if we really have several areas
                for (AreaType area : areas) {

                    // If it is a ploygon the use the first point for now
                    if (area.sizeOfAreaPolygonGeographicCoordinateArray() > 0) {
                        // TODO: should get centroid of polygon not just the first point
                        TwoDimensionalGeographicCoordinateType firstPoint = area.getAreaPolygonGeographicCoordinateArray(0);
                        String latitude = Common.fromDegMinSec(firstPoint.getGeographicCoordinateLatitude());
                        fieldValueMap.put(UICDSIncidentsBoard.LATITUDE_FIELD, latitude);
                        String longitude = Common.fromDegMinSec(firstPoint.getGeographicCoordinateLongitude());
                        fieldValueMap.put(UICDSIncidentsBoard.LONGITUDE_FIELD, longitude);
                    }
                    // if circle area present
                    else if (area.sizeOfAreaCircularRegionArray() > 0) {
                        if (area.getAreaCircularRegionArray(0).sizeOfCircularRegionCenterCoordinateArray() > 0) {
                            for (TwoDimensionalGeographicCoordinateType center : area.getAreaCircularRegionArray(
                                0).getCircularRegionCenterCoordinateArray()) {
                                // TwoDimensionalGeographicCoordinateType center = area
                                // .getAreaCircularRegionArray(0)
                                // .getCircularRegionCenterCoordinateArray(0);
                                // LengthMeasureType type =
                                // area.getAreaCircularRegionArray(0).getCircularRegionRadiusLengthMeasureArray(
                                // 0);
                                // LengthCodeType code = type.getLengthUnitCode();
                                // LatitudeCoordinateType lat =
                                // center.getGeographicCoordinateLatitude();
                                // DatumCodeType datum = center.getGeographicDatumCode();
                                String latitude = Common.fromDegMinSec(center.getGeographicCoordinateLatitude());
                                fieldValueMap.put(UICDSIncidentsBoard.LATITUDE_FIELD, latitude);
                                String longitude = Common.fromDegMinSec(center.getGeographicCoordinateLongitude());
                                fieldValueMap.put(UICDSIncidentsBoard.LONGITUDE_FIELD, longitude);
                            }
                        }
                    }
                }
            }
        }

        // Get the jurisdiction/contact information
        if (incident.getIncident().sizeOfIncidentJurisdictionalOrganizationArray() > 0) {
            OrganizationType organization = incident.getIncident().getIncidentJurisdictionalOrganizationArray(
                0);
            // jurisdiction
            if (organization.sizeOfOrganizationNameArray() > 0) {
                fieldValueMap.put(UICDSIncidentsBoard.JURISDICTION_FIELD,
                    organization.getOrganizationNameArray(0).getStringValue());
            }
            // contact info
            if (organization.sizeOfOrganizationPrincipalOfficialArray() > 0) {
                if (organization.getOrganizationPrincipalOfficialArray(0).sizeOfPersonNameArray() > 0) {
                    if (organization.getOrganizationPrincipalOfficialArray(0).getPersonNameArray(0).sizeOfPersonFullNameArray() > 0) {
                        fieldValueMap.put(
                            UICDSIncidentsBoard.CONTACT_NAME_FIELD,
                            organization.getOrganizationPrincipalOfficialArray(0).getPersonNameArray(
                                0).getPersonFullNameArray(0).getStringValue());
                        fieldValueMap.put(UICDSIncidentsBoard.CONTACT_PHONE_FIELD, "555-5555");
                    }
                }
            }
        }

        // Get incident type
        if (incident.getIncident().sizeOfActivityCategoryTextArray() > 0) {
            fieldValueMap.put(UICDSIncidentsBoard.EVENT_TYPE_FIELD,
                incident.getIncident().getActivityCategoryTextArray(0).getStringValue());
        }
        return fieldValueMap;
    }

    @Override
    public void handleWorkProductPublicationMessage(
        WorkProductPublicationResponseType workProductPublicationResponse) {

        // TODO Auto-generated method stub

    }

    @Override
    public void handleEDXLDEMessage(EDXLDistribution edxldeMessage) {

        // This implementation does not process EDXL-DE messages.

    }

    public boolean isHtmlString(String incomingStr)
    {
    	//to be done after bill string format ready.
    	return true;
    }
}

/**
 * 
 */
package com.saic.uicds.clients.em.webeocAdapter;

import gov.niem.niem.niemCore.x20.AddressFullTextDocument;
import gov.niem.niem.niemCore.x20.AreaType;
import gov.niem.niem.niemCore.x20.TwoDimensionalGeographicCoordinateType;
import gov.ucore.ucore.x20.DigestType;
import gov.ucore.ucore.x20.EventType;

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
import org.uicds.incident.UICDSIncidentType;
import org.uicds.incidentManagementService.UpdateIncidentRequestDocument;
import org.uicds.incidentManagementService.UpdateIncidentResponseDocument;
import org.uicds.notificationService.WorkProductDeletedNotificationType;
import org.uicds.workProductService.WorkProductPublicationResponseType;

import x0.comEsi911Webeoc7Api1.GetDataByDataIdResponseDocument;
import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;

import com.saic.precis.x2009.x06.base.IdentificationType;
import com.saic.precis.x2009.x06.base.ProcessingStateType;
import com.saic.precis.x2009.x06.base.ProcessingStateType.Enum;
import com.saic.precis.x2009.x06.base.PropertiesType;
import com.saic.precis.x2009.x06.base.StateType;
import com.saic.precis.x2009.x06.structures.WorkProductDocument.WorkProduct;
import com.saic.uicds.clients.em.async.UicdsCore;
import com.saic.uicds.clients.em.async.UicdsIncident;
import com.saic.uicds.clients.em.async.UicdsWorkProduct;
import com.saic.uicds.clients.em.async.WorkProductListener;
import com.saic.uicds.clients.util.Common;

/**
 * Create a WorkProductListener that will create an entry on the UICDS Position Log Board when a new
 * UICDS incident is created.
 * 
 * @author roger
 * 
 */
public class IncidentToUICDSPositionLog
    implements WorkProductListener {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private UicdsCore uicdsCore;

    private WebEOCWebServiceClient webEocClient;

    private UICDSPositionLogBoard uicdsPositionLogBoard;

    private ArrayList<WorkProduct> workProductQueue = new ArrayList<WorkProduct>();

    // key = ACT
    private HashMap<String, IdentificationType> pendingWorkProductUpdates = new HashMap<String, IdentificationType>();

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
     * @return the uicdsPositionLogBoard
     */
    public UICDSPositionLogBoard getUicdsPositionLogBoard() {

        return uicdsPositionLogBoard;
    }

    /**
     * @param uicdsPositionLogBoard the uicdsPositionLogBoard to set
     */
    public void setUicdsPositionLogBoard(UICDSPositionLogBoard uicdsPositionLogBoard) {

        this.uicdsPositionLogBoard = uicdsPositionLogBoard;
    }

    public int sizeOfQueuedWorkProducts() {

        return workProductQueue.size();
    }

    public int numberOfPendingWorkProductUpdates() {

        return pendingWorkProductUpdates.size();
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

    @Override
    public void handleWorkProductPublicationMessage(
        WorkProductPublicationResponseType workProductPublicationResponse) {

        // Get info about work product that was pending
        String act = workProductPublicationResponse.getWorkProductProcessingStatus().getACT().getStringValue();
        IdentificationType wpid = Common.getIdentificationElement(workProductPublicationResponse.getWorkProduct());

        IdentificationType pendingID = pendingWorkProductUpdates.get(act);
        if (pendingID == null) {
            logger.error("NULL work product id in pending work products map");
            return;
        }

        // Remove the entry from the pending map
        pendingWorkProductUpdates.remove(act);

        // TODO: If REJECTED should try the update again by getting the current version of the work
        // product,
        // the current data from the board and merging them somehow. For now we just report the
        // problem.
        if (workProductPublicationResponse.getWorkProductProcessingStatus().getStatus() == ProcessingStateType.REJECTED) {
            StringBuffer sb = new StringBuffer();
            sb.append("Work product update REJECTED for ACT: ");
            sb.append(act);
            sb.append(" for work product: ");
            sb.append(pendingID);
            sb.append(wpid.xmlText());
            logger.error(sb.toString());
        } else {
            logger.warn("Work product update ACCEPTED for ACT: " + act);
        }
    }

    private void processWorkProduct(WorkProduct workProduct) {

        IdentificationType workProductIdentifier = Common.getIdentificationElement(workProduct);

        // Only handle Incident updates
        if (!workProductIdentifier.getType().getStringValue().equalsIgnoreCase(
            UicdsIncident.INCIDENT_WP_TYPE)) {
            return;
        }

        PropertiesType properties = Common.getPropertiesElement(workProduct);
        DigestType digest = Common.getDigest(workProduct);

        // This is a one way listener so cannot start an infinite loop of updates.
        // It only updates the Incident Work Product once when it is created so
        // there's no harm in letting that update come back through.

        // Get the full work product
        IncidentDocument incident = getIncidentDocument(workProductIdentifier);

        try {
            processUpdateFromOtherUICDSClients(workProductIdentifier, properties, digest, incident);
        } catch (WebEOCOperationException e) {
            workProductQueue.add(workProduct);
        }
    }

    private void processUpdateFromOtherUICDSClients(IdentificationType workProductIdentifier,
        PropertiesType properties, DigestType digest, IncidentDocument incident)
        throws WebEOCOperationException {

        String interestGroupID = null;
        if (properties.getAssociatedGroups().sizeOfIdentifierArray() > 0) {
            interestGroupID = properties.getAssociatedGroups().getIdentifierArray(0).getStringValue();
            logger.debug("Received UICDS incident notification: " + interestGroupID);
        }

        // If the incident is null then the incident has probably been archived already so close it
        if (incident == null || incident.getIncident() == null) {
            int dataid = findCachedIncident(digest);
            if (dataid != 0) {
                closeWebEOCRecord(dataid);
            } else {
                logger.error("Cannot find WebEOC cached entry for incident " + interestGroupID);
            }
        } else {
            // Get the associated WebEOC record dataid if it exists
            int webEocDataID = webEOCRecordExistsForIncident(incident, digest);

            // Close the WebEOC entry if the incident document is null (archived already) or empty
            if (incident == null || !incident.getIncident().getDomNode().hasChildNodes()
                || workProductIdentifier.getState().equals(StateType.INACTIVE)) {
                closeWebEOCRecord(webEocDataID);
            }
            // Else update the entry
            else {
                updateWebEOCRecord(workProductIdentifier, incident, webEocDataID);
            }
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
                logger.info("Creating entry on UICDS Position Log board for incident "
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
                webEocClient.updateDataOnBoard(uicdsPositionLogBoard,
                    uicdsPositionLogBoard.getIncidentName(), webEocDataID, fieldMap);
            } else {
                logger.error("no field values to update the incident with");
            }
        }
    }

    public void createNewWebEOCEntry(IdentificationType workProductIdentifier,
        IncidentDocument incident, Map<String, String> fieldMap) throws WebEOCOperationException {

        int dataid = webEocClient.addDataToBoard(uicdsPositionLogBoard,
            uicdsPositionLogBoard.getIncidentName(), fieldMap);

        // Add the new record to the board's cache
        GetDataByDataIdResponseDocument response = webEocClient.getDataByIdFromBoard(
            uicdsPositionLogBoard, dataid);
        try {
            if (response.getGetDataByDataIdResponse() != null
                && response.getGetDataByDataIdResponse().getGetDataByDataIdResult() != null) {
                XmlObject[] records;
                records = WebEOCUtils.getRecordsFromData(response.getGetDataByDataIdResponse().getGetDataByDataIdResult());
                if (records.length != 1) {
                    logger.error("found more that one record for dataid: " + dataid);
                } else {
                    uicdsPositionLogBoard.addCachedItem(records[0]);
                }
            }

            // Update the incident document with information about this WebEOC entry
            updateUICDSIncidentDocumentWithWebEOCRecordID(workProductIdentifier, incident, dataid);
        } catch (XmlException e) {
            logger.error("Error parsing new WebEOC record for new incident so not added to local cache: "
                + e.getMessage());
        }
    }

    public void updateUICDSIncidentDocumentWithWebEOCRecordID(
        IdentificationType workProductIdentifier, IncidentDocument incident, int dataid) {

        UICDSIncidentType incidentType = WebEOCUtils.addIncidentEvent(incident.getIncident(),
            Constants.WEBEOC_RECEIVED_REASON, Constants.WEBEOC_OWNER_NAME,
            Integer.toString(dataid), UICDSPositionLogBoard.UICDS_POSITION_BOARD_NAME,
            webEocClient.getServerName());
        updateOnCore(workProductIdentifier, incidentType);
    }

    /**
     * Update the incident on the core
     * 
     * @param incidentWorkProduct
     */
    private void updateOnCore(IdentificationType workProductIdentifier,
        UICDSIncidentType incidentType) {

        UpdateIncidentRequestDocument request = UpdateIncidentRequestDocument.Factory.newInstance();
        request.addNewUpdateIncidentRequest().setIncident(incidentType);
        request.getUpdateIncidentRequest().addNewWorkProductIdentification().set(
            workProductIdentifier);

        try {
            UpdateIncidentResponseDocument response = (UpdateIncidentResponseDocument) uicdsCore.marshalSendAndReceive(request);

            Enum status = response.getUpdateIncidentResponse().getWorkProductPublicationResponse().getWorkProductProcessingStatus().getStatus();

            // If the update was rejected then try again the next time around
            if (status == ProcessingStateType.REJECTED) {
                logger.error("Update of incident was REJECTED");
            }
            // If pending then check status again when the notification of final status arrives from
            // notification
            else if (status == ProcessingStateType.PENDING) {
                String act = response.getUpdateIncidentResponse().getWorkProductPublicationResponse().getWorkProductProcessingStatus().getACT().getStringValue();
                pendingWorkProductUpdates.put(act, workProductIdentifier);
                logger.warn("Update of incident is PENDING with ACT: " + act);
            }

        } catch (ClassCastException e) {
            logger.error("Error casting response to UpdateIncidentResponseDocument");
        }

    }

    private void closeWebEOCRecord(int webEocDataID) throws WebEOCOperationException {

        // Else update with last values and close the WebEOC entry
        if (webEocDataID != 0) {
            XmlObject record = uicdsPositionLogBoard.getBoardEntryXmlObject(webEocDataID);
            if (record != null) {
                Map<String, String> fieldMap = WebEOCUtils.getFieldMapFromRecord(record);
                if (fieldMap.size() > 1) {
                    fieldMap.put(UICDSPositionLogBoard.DESCRIPTION_FIELD,
                        "(CLOSED) " + fieldMap.get(UICDSPositionLogBoard.DESCRIPTION_FIELD));
                    logger.info("Closing entry " + webEocDataID);
                    webEocClient.updateDataOnBoard(uicdsPositionLogBoard,
                        uicdsPositionLogBoard.getIncidentName(), webEocDataID, fieldMap);
                } else {
                    logger.error("Could not get field map for entry " + webEocDataID);
                }
            } else {
                logger.error("No record found to close");
            }
        } else {
            logger.error("Could not find matching record from WebEOC");
        }
    }

    public HashMap<String, String> getFieldMapForIncident(IncidentDocument incident,
        StateType.Enum state) {

        // Get a map of the fields to post to UICDS Position Log entry
        HashMap<String, String> fieldMap = mapIncidentToPositionLogFields(incident);
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

    private int webEOCRecordExistsForIncident(IncidentDocument incident, DigestType digest)
        throws WebEOCOperationException {

        int dataid = 0;

        // Look first in the incident document. This will only be set if this is an update to the
        // work product
        dataid = WebEOCUtils.findWebEOCDataID(incident.getIncident(),
            uicdsPositionLogBoard.getBoardName());

        // If not found see if there is a matching item in the local cache. First match by name and
        // then try to
        // narrow down to one record with the activity type. If there are still more than one then
        if (dataid == 0) {
            dataid = findCachedIncident(incident);
        }
        return dataid;
    }

    public int findCachedIncident(IncidentDocument incident) {

        int dataid = 0;
        ArrayList<String> possibleDataIDs = new ArrayList<String>();
        XmlObject[] records = null;
        if (incident.getIncident().sizeOfActivityNameArray() > 0) {
            records = uicdsPositionLogBoard.getBoardEntryXmlObjectByFieldValue(
                UICDSPositionLogBoard.NAME_FIELD,
                incident.getIncident().getActivityNameArray(0).getStringValue());
        }
        if (records != null && records.length > 0
            && incident.getIncident().sizeOfActivityCategoryTextArray() > 0) {
            for (XmlObject record : records) {
                String activityCategory = WebEOCUtils.getAttributeFromRecord(
                    UICDSPositionLogBoard.EVENT_TYPE_FIELD, record);
                if (incident.getIncident().getActivityCategoryTextArray(0).getStringValue().equals(
                    activityCategory)) {
                    String dataidStr = WebEOCUtils.getAttributeFromRecord(Board.DATAID_FIELD,
                        record);
                    possibleDataIDs.add(dataidStr);
                }
            }
        }
        if (possibleDataIDs.size() == 1) {
            try {
                dataid = Integer.parseInt(possibleDataIDs.get(0));
            } catch (NumberFormatException e) {
                logger.error("Error parsing dataid from incident: " + e.getMessage());
            }
        }
        return dataid;
    }

    public int findCachedIncident(DigestType digest) {

        int dataid = 0;
        ArrayList<EventType> events = Common.getEvents(digest);
        if (events.size() > 1) {
            logger.error("Incident digest has more than one Event, may not be able to match to WebEOC entry");
        }

        ArrayList<String> possibleDataIDs = new ArrayList<String>();
        XmlObject[] records = null;
        for (EventType event : events) {
            if (event.sizeOfIdentifierArray() > 0) {
                records = uicdsPositionLogBoard.getBoardEntryXmlObjectByFieldValue(
                    UICDSPositionLogBoard.NAME_FIELD, event.getIdentifierArray(0).getStringValue());
            } else {
                logger.error("No Event elements found in digest: " + digest);
            }
            if (records != null && records.length > 0 && event.getDescriptor() != null) {
                for (XmlObject record : records) {
                    String description = WebEOCUtils.getAttributeFromRecord(
                        UICDSPositionLogBoard.DESCRIPTION_FIELD, record);
                    if (event.getDescriptor().getStringValue().equals(description)) {
                        String dataidStr = WebEOCUtils.getAttributeFromRecord(Board.DATAID_FIELD,
                            record);
                        possibleDataIDs.add(dataidStr);
                    } else {
                        logger.error("Descriptions do not match " + event.getDescriptor() + " and "
                            + description);
                    }
                }
            } else {
                logger.error("Have Event elements but no descriptor in digest: " + digest);
            }
        }

        if (possibleDataIDs.size() == 1) {
            try {
                dataid = Integer.parseInt(possibleDataIDs.get(0));
            } catch (NumberFormatException e) {
                logger.error("Error parsing dataid from digest: " + e.getMessage());
            }
        }
        return dataid;
    }

    private HashMap<String, String> mapIncidentToPositionLogFields(IncidentDocument incident) {

        // build up map of incident values
        HashMap<String, String> fieldValueMap = new HashMap<String, String>();

        if (incident.getIncident() == null) {
            return fieldValueMap;
        }

        // incident name
        if (incident.getIncident().sizeOfActivityNameArray() > 0) {
            fieldValueMap.put(UICDSPositionLogBoard.NAME_FIELD,
                incident.getIncident().getActivityNameArray(0).getStringValue());
        }

        // incident description
        if (incident.getIncident().sizeOfActivityDescriptionTextArray() > 0) {
            fieldValueMap.put(UICDSPositionLogBoard.DESCRIPTION_FIELD,
                incident.getIncident().getActivityDescriptionTextArray(0).getStringValue());
        }

        // UICDS incident id
        if (incident.getIncident().sizeOfActivityIdentificationArray() > 0) {
            if (incident.getIncident().getActivityIdentificationArray(0).sizeOfIdentificationIDArray() > 0) {
                fieldValueMap.put(
                    UICDSPositionLogBoard.GLOBALID_FIELD,
                    incident.getIncident().getActivityIdentificationArray(0).getIdentificationIDArray(
                        0).getStringValue());
            }
        }

        if (incident.getIncident().sizeOfActivityDateRepresentationArray() > 0) {
        	
        	  XmlCursor cursor = incident.getIncident().getActivityDateRepresentationArray(0).newCursor();
              String calendarString = cursor.getTextValue();
              cursor.dispose();
              
            SimpleDateFormat ISO8601Local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            TimeZone timeZone = TimeZone.getDefault();
            ISO8601Local.setTimeZone(timeZone);
            
            try {
	            Date dateTime = (Date) ISO8601Local.parse(calendarString.trim());
	            fieldValueMap.put(UICDSPositionLogBoard.DATA_TIME_FIELD, ISO8601Local.format(dateTime));
            }
	         catch (ParseException e) {
	            System.err.println("Error parsing date string should be yyyy-MM-dd'T'HH:mm:ss format: "
	                + e.getMessage());
	        }
            
      //      Date date = Common.getISO8601LocalDateFromActivityDate(
      //          incident.getIncident().getActivityDateRepresentationArray(0), ISO8601Local);
      //      fieldValueMap.put(UICDSPositionLogBoard.DATA_TIME_FIELD, ISO8601Local.format(date));

            // XmlCursor cursor =
            // incident.getIncident().getActivityDateRepresentationArray(0).newCursor();
            // String calendarString = cursor.getTextValue();
            // cursor.dispose();
            // if (calendarString != null && calendarString.length() > 0) {
            // SimpleDateFormat ISO8601Local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            // TimeZone timeZone = TimeZone.getDefault();
            // ISO8601Local.setTimeZone(timeZone);
            // try {
            // Date dateTime = (Date) ISO8601Local.parse(calendarString.trim());
            // fieldValueMap.put(UICDSPositionLogBoard.DATA_TIME_FIELD,
            // ISO8601Local.format(dateTime));
            // } catch (ParseException e) {
            // System.err.println("Error parsing date string should be yyyy-MM-dd'T'HH:mm:ss format: "
            // + e.getMessage());
            // }
            // }
        }

        // Get the location address
        if (incident.getIncident().sizeOfIncidentLocationArray() > 0) {
            // get address
            if (incident.getIncident().getIncidentLocationArray(0).sizeOfLocationAddressArray() > 0) {

                AddressFullTextDocument fullText = Common.getFullTextAddressFromLocationAddressArray(incident.getIncident().getIncidentLocationArray(
                    0).getLocationAddressArray(0));
                fieldValueMap.put(UICDSPositionLogBoard.EVENT_LOCATION_FIELD,
                    fullText.getAddressFullText().getStringValue());
            }

            // get lat/lon and append as (latitude,longitude) to the event location data
            if (incident.getIncident().getIncidentLocationArray(0).sizeOfLocationAreaArray() > 0) {
                String latitude = null;
                String longitude = null;
                AreaType[] areas = incident.getIncident().getIncidentLocationArray(0).getLocationAreaArray();
                // TODO: figure out what to do if we really have several areas
                for (AreaType area : areas) {

                    // If it is a ploygon the use the first point for now
                    if (area.sizeOfAreaPolygonGeographicCoordinateArray() > 0) {
                        // TODO: should get centroid of polygon not just the first point
                        TwoDimensionalGeographicCoordinateType firstPoint = area.getAreaPolygonGeographicCoordinateArray(0);
                        latitude = Common.fromDegMinSec(firstPoint.getGeographicCoordinateLatitude());
                        longitude = Common.fromDegMinSec(firstPoint.getGeographicCoordinateLongitude());
                    }
                    // if circle area present
                    else if (area.sizeOfAreaCircularRegionArray() > 0) {
                        if (area.getAreaCircularRegionArray(0).sizeOfCircularRegionCenterCoordinateArray() > 0) {
                            for (TwoDimensionalGeographicCoordinateType center : area.getAreaCircularRegionArray(
                                0).getCircularRegionCenterCoordinateArray()) {
                                latitude = Common.fromDegMinSec(center.getGeographicCoordinateLatitude());
                                longitude = Common.fromDegMinSec(center.getGeographicCoordinateLongitude());
                            }
                        }
                    }
                }
                if (latitude != null && longitude != null) {
                    StringBuffer sb = new StringBuffer();
                    sb.append(fieldValueMap.get(UICDSPositionLogBoard.EVENT_LOCATION_FIELD));
                    sb.append(" (");
                    sb.append(latitude);
                    sb.append(",");
                    sb.append(longitude);
                    sb.append(")");
                    fieldValueMap.put(UICDSPositionLogBoard.EVENT_LOCATION_FIELD, sb.toString());
                }
            }
        }

        // Get incident type
        if (incident.getIncident().sizeOfActivityCategoryTextArray() > 0) {
            fieldValueMap.put(UICDSPositionLogBoard.EVENT_TYPE_FIELD,
                incident.getIncident().getActivityCategoryTextArray(0).getStringValue());
        }
        return fieldValueMap;
    }

    @Override
    public void handleEDXLDEMessage(EDXLDistribution edxldeMessage) {

        // This implementation does not process EDXL-DE messages.

    }

}

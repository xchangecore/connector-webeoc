package com.saic.uicds.clients.em.webeocAdapter;

import java.io.FileNotFoundException;
import java.util.Map;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.ws.client.WebServiceTransportException;
import org.springframework.ws.client.core.WebServiceOperations;
import org.springframework.ws.soap.client.SoapFaultClientException;

import x0.comEsi911Webeoc7Api1.AddDataDocument;
import x0.comEsi911Webeoc7Api1.AddDataResponseDocument;
import x0.comEsi911Webeoc7Api1.ArrayOfString;
import x0.comEsi911Webeoc7Api1.GetDataByDataIdDocument;
import x0.comEsi911Webeoc7Api1.GetDataByDataIdResponseDocument;
import x0.comEsi911Webeoc7Api1.GetDataDocument;
import x0.comEsi911Webeoc7Api1.GetDataResponseDocument;
import x0.comEsi911Webeoc7Api1.GetFilteredDataDocument;
import x0.comEsi911Webeoc7Api1.GetFilteredDataResponseDocument;
import x0.comEsi911Webeoc7Api1.GetIncidentsDocument;
import x0.comEsi911Webeoc7Api1.GetIncidentsResponseDocument;
import x0.comEsi911Webeoc7Api1.UpdateDataDocument;
import x0.comEsi911Webeoc7Api1.UpdateDataResponseDocument;
import x0.comEsi911Webeoc7Api1.WebEOCCredentials;

import com.saic.uicds.clients.util.Common;

public class WebEOCWebServiceClientImpl implements WebEOCWebServiceClient {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    private WebServiceOperations webServiceTemplate;

    WebEOCWebServiceClientConfig webEOCWebServiceClientConfig;

    public WebServiceOperations getWebServiceTemplate() {

        return webServiceTemplate;
    }

    public void setWebServiceTemplate(WebServiceOperations webServiceTemplate) {

        this.webServiceTemplate = webServiceTemplate;
    }

    public WebEOCWebServiceClientConfig getWebEOCWebServiceClientConfig() {

        return webEOCWebServiceClientConfig;
    }

    public void setWebEOCWebServiceClientConfig(
        WebEOCWebServiceClientConfig webEOCWebServiceClientConfig) {

        this.webEOCWebServiceClientConfig = webEOCWebServiceClientConfig;
    }

    public WebEOCWebServiceClientImpl() {

    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#getUserName()
     */
    @Override
    public String getUserName() {

        return webEOCWebServiceClientConfig.getUser();
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#getPosition()
     */
    @Override
    public String getPosition() {

        return webEOCWebServiceClientConfig.getPosition();
    }

    @Override
    public String getServerName() {

        return webEOCWebServiceClientConfig.getHostURL();
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#getIncidentList()
     */
    @Override
    public ArrayOfString getIncidentList() {

        GetIncidentsDocument request = GetIncidentsDocument.Factory.newInstance();
        request.addNewGetIncidents().addNewCredentials().setUsername(
            webEOCWebServiceClientConfig.getUser());
        request.getGetIncidents().getCredentials().setPassword(
            webEOCWebServiceClientConfig.getPassword());
        request.getGetIncidents().getCredentials().setPosition(
            webEOCWebServiceClientConfig.getPosition());

        GetIncidentsResponseDocument response = null;

        try {
            response = (GetIncidentsResponseDocument) webServiceTemplate.marshalSendAndReceive(
                request, new org.springframework.ws.soap.client.core.SoapActionCallback(
                    "urn:com:esi911:webeoc7:api:1.0/GetIncidents"));
        } catch (SoapFaultClientException soapEx) {
            logger.error("====> ERROR: caught exception from webEOC GetIncidents, SoapFaultClientException=" +
                Common.getFaultDetailedDescription(webServiceTemplate, soapEx, logger));

        } catch (WebServiceTransportException wsTransEx) {
            logger.error("====> ERROR: caught exception from webEOC GetIncidents, WebServiceTransportException=" +
                wsTransEx.getMessage());
        } catch (Throwable e) {
            logger.error("====> ERROR: caught exception from webEOC GetIncidents, exception=" +
                e.toString());
        }

        if (response != null) {
            if (response.getGetIncidentsResponse() != null &&
                response.getGetIncidentsResponse().getGetIncidentsResult() != null) {
                return response.getGetIncidentsResponse().getGetIncidentsResult();
            }
        }

        return null;
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#getFilteredDataFromBoard(com.saic.uicds.clients.em.webeocAdapter.Board, java.util.Map)
     */
    @Override
    public GetFilteredDataResponseDocument getFilteredDataFromBoard(Board board,
        Map<String, String> filters) throws WebEOCOperationException {

        GetFilteredDataDocument request = GetFilteredDataDocument.Factory.newInstance();
        setCredentials(request.addNewGetFilteredData().addNewCredentials(), board);
        request.getGetFilteredData().setBoardName(board.getBoardName());
        request.getGetFilteredData().setDisplayViewName(board.getViewName());
        XmlObject filter = createFilter(filters);
        request.getGetFilteredData().setXmlUserFilter(filter.xmlText());

        GetFilteredDataResponseDocument response = null;
        try {
            response = (GetFilteredDataResponseDocument) webServiceTemplate.marshalSendAndReceive(
                request, new org.springframework.ws.soap.client.core.SoapActionCallback(
                    "urn:com:esi911:webeoc7:api:1.0/GetFilteredData"));
        } catch (SoapFaultClientException soapEx) {
            logger.error("====> ERROR: caught exception from webEOC GetFilteredData, SoapFaultClientException=" +
                Common.getFaultDetailedDescription(webServiceTemplate, soapEx, logger));
            throw new WebEOCOperationException(Common.getFaultDetailedDescription(
                webServiceTemplate, soapEx, logger));
        } catch (WebServiceTransportException wsTransEx) {
            logger.error("====> ERROR: caught exception from webEOC GetFilteredData, WebServiceTransportException=" +
                wsTransEx.getMessage());
            throw new WebEOCOperationException(wsTransEx.getMessage());
        } catch (Throwable e) {
            logger.error("====> ERROR: caught exception from webEOC GetFilteredData, exception=" +
                e.toString());
            throw new WebEOCOperationException(e.getMessage());
        }
        return response;
    }

    private XmlObject createFilter(Map<String, String> filters) {

        XmlObject xml = XmlObject.Factory.newInstance();
        XmlCursor cursor = xml.newCursor();
        cursor.toNextToken();
        cursor.beginElement("data");
        for (String element : filters.keySet()) {
            cursor.insertElementWithText(element, filters.get(element));
        }
        cursor.dispose();
        return xml;
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#getDataFromBoard(com.saic.uicds.clients.em.webeocAdapter.Board)
     */
    @Override
    public GetDataResponseDocument getDataFromBoard(Board board) {

        GetDataDocument request = GetDataDocument.Factory.newInstance();
        setCredentials(request.addNewGetData().addNewCredentials(), board);
        request.getGetData().setBoardName(board.getBoardName());
        request.getGetData().setDisplayViewName(board.getViewName());

        GetDataResponseDocument response = null;
        try {
            // logger.info("xxx" + request.toString());

            response = (GetDataResponseDocument) webServiceTemplate.marshalSendAndReceive(request,
                new org.springframework.ws.soap.client.core.SoapActionCallback(
                    "urn:com:esi911:webeoc7:api:1.0/GetData"));
        } catch (SoapFaultClientException soapEx) {
            soapEx.printStackTrace();
            logger.error("====> ERROR: caught exception from webEOC GetData, SoapFaultClientException=" +
                Common.getFaultDetailedDescription(webServiceTemplate, soapEx, logger));

        } catch (WebServiceTransportException wsTransEx) {
            logger.error("====> ERROR: caught exception from webEOC GetData, WebServiceTransportException=" +
                wsTransEx.getMessage());
        } catch (Throwable e) {
            logger.error("====> ERROR: caught exception from webEOC GetData, exception=" +
                e.toString());
        }

        return response;
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#getDataByIdFromBoard(com.saic.uicds.clients.em.webeocAdapter.Board, java.lang.String)
     */
    @Override
    public GetDataByDataIdResponseDocument getDataByIdFromBoard(Board board, int dataid) {

        GetDataByDataIdDocument request = GetDataByDataIdDocument.Factory.newInstance();
        setCredentials(request.addNewGetDataByDataId().addNewCredentials(), board);
        request.getGetDataByDataId().setBoardName(board.getBoardName());
        request.getGetDataByDataId().setDisplayViewName(board.getViewName());
        request.getGetDataByDataId().setDataId(dataid);

        GetDataByDataIdResponseDocument response = null;
        try {
            response = (GetDataByDataIdResponseDocument) webServiceTemplate.marshalSendAndReceive(
                request, new org.springframework.ws.soap.client.core.SoapActionCallback(
                    "urn:com:esi911:webeoc7:api:1.0/GetDataByDataId"));
        } catch (SoapFaultClientException soapEx) {
            logger.error("====> ERROR: caught exception from webEOC GetData, SoapFaultClientException=" +
                Common.getFaultDetailedDescription(webServiceTemplate, soapEx, logger));

        } catch (WebServiceTransportException wsTransEx) {
            logger.error("====> ERROR: caught exception from webEOC GetData, WebServiceTransportException=" +
                wsTransEx.getMessage());
        } catch (Throwable e) {
            logger.error("====> ERROR: caught exception from webEOC GetData, exception=" +
                e.toString());
        }

        return response;
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#addDataToBoard(com.saic.uicds.clients.em.webeocAdapter.Board, java.lang.String, java.util.HashMap)
     */
    @Override
    public int addDataToBoard(Board board, String incidentName, Map<String, String> map)
        throws WebEOCOperationException {

        AddDataDocument request = AddDataDocument.Factory.newInstance();
        setCredentials(request.addNewAddData().addNewCredentials(), board);
        if (board.getInputViewName() != null) {
            request.getAddData().setInputViewName(board.getInputViewName());
        } else {
            request.getAddData().setInputViewName(board.getViewName());
        }
        request.getAddData().setBoardName(board.getBoardName());

        String xmlData = WebEOCUtils.createXmlDataFromMap(map);
        // logger.debug("addDataToBoard: " + xmlData);

        request.getAddData().setXmlData(xmlData);

        AddDataResponseDocument response = null;

        try {
            response = (AddDataResponseDocument) webServiceTemplate.marshalSendAndReceive(request,
                new org.springframework.ws.soap.client.core.SoapActionCallback(
                    "urn:com:esi911:webeoc7:api:1.0/AddData"));
            // logger.debug("addDataToBoard: response: " + response);
            return response.getAddDataResponse().getAddDataResult();
        } catch (SoapFaultClientException soapEx) {
            logger.error("====> ERROR: caught exception from webEOC AddData, SoapFaultClientException=" +
                Common.getFaultDetailedDescription(webServiceTemplate, soapEx, logger));

        } catch (WebServiceTransportException wsTransEx) {
            logger.error("====> ERROR: caught exception from webEOC AddData, WebServiceTransportException=" +
                wsTransEx.getMessage());
        } catch (Throwable e) {
            logger.error("====> ERROR: caught exception from webEOC AddData, exception=" +
                e.toString());
        }
        return 0;
    }

    /* (non-Javadoc)
     * @see com.saic.uicds.clients.em.webeocAdapter.WebEOCWebServiceClient#updateDataOnBoard(com.saic.uicds.clients.em.webeocAdapter.Board, java.lang.String, int, java.util.HashMap)
     */
    @Override
    public int updateDataOnBoard(Board board, String incidentName, int dataid,
        Map<String, String> map) throws WebEOCOperationException {

        logger.info("updating " + dataid + " on board ", board.getBoardName());
        UpdateDataDocument request = UpdateDataDocument.Factory.newInstance();
        setCredentials(request.addNewUpdateData().addNewCredentials(), board);
        request.getUpdateData().setDataId(dataid);
        if (board.getInputViewName() != null) {
            request.getUpdateData().setInputViewName(board.getInputViewName());
        } else {
            request.getUpdateData().setInputViewName(board.getViewName());
        }
        request.getUpdateData().setBoardName(board.getBoardName());

        String xmlData = WebEOCUtils.createXmlDataFromMap(map);

        request.getUpdateData().setXmlData(xmlData);

        UpdateDataResponseDocument response = null;

        // System.out.println(request);

        try {
            response = (UpdateDataResponseDocument) webServiceTemplate.marshalSendAndReceive(
                request, new org.springframework.ws.soap.client.core.SoapActionCallback(
                    "urn:com:esi911:webeoc7:api:1.0/UpdateData"));
            return response.getUpdateDataResponse().getUpdateDataResult();
        } catch (SoapFaultClientException soapEx) {
            logger.error("====> ERROR: caught exception from webEOC UpdateData, SoapFaultClientException=" +
                Common.getFaultDetailedDescription(webServiceTemplate, soapEx, logger));

        } catch (WebServiceTransportException wsTransEx) {
            logger.error("====> ERROR: caught exception from webEOC UpdateDAta, WebServiceTransportException=" +
                wsTransEx.getMessage());
        } catch (Throwable e) {
            logger.error("====> ERROR: caught exception from webEOC UpdateData, exception=" +
                e.toString());
        }
        return 0;
    }

    @Override
    public void setCredentialsEx(WebEOCCredentials credentials, Board board) {

        credentials.setUsername(webEOCWebServiceClientConfig.getUser());
        credentials.setPassword(webEOCWebServiceClientConfig.getPassword());
        credentials.setPosition(webEOCWebServiceClientConfig.getPosition());
        credentials.setIncident(board.getIncidentName());

    }

    private void setCredentials(WebEOCCredentials credentials, Board board) {

        credentials.setUsername(webEOCWebServiceClientConfig.getUser());
        credentials.setPassword(webEOCWebServiceClientConfig.getPassword());
        credentials.setPosition(webEOCWebServiceClientConfig.getPosition());
        /*
        String boardName = webEOCWebServiceClientConfig.getMemamBoardName();
        board.setBoardName(boardName);
        
        String ipvn = webEOCWebServiceClientConfig.getMemamInputViewName();
        board.setInputViewName(ipvn);
        
        String vn = webEOCWebServiceClientConfig.getMemamViewName();
        board.setViewName(vn);
                   */

        credentials.setIncident(board.getIncidentName());

    }

    public static void main(String[] args) {

        // final String CONTEXT = "webEOCWebService-test.xml";

        final String CONTEXT = "webeoc-context.xml";
        ApplicationContext context = null;
        try {
            context = new FileSystemXmlApplicationContext("./" + CONTEXT);
            System.out.println("Using local context file: " + CONTEXT);
        } catch (BeansException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                System.out.println("Local context File not found so using file from jar");
            } else {
                System.out.println("Error reading local file context: " + e.getCause().getMessage());
            }
        }

        if (context == null) {
            context = new ClassPathXmlApplicationContext(new String[] { "contexts/" + CONTEXT });
        }

        WebEOCWebServiceClient client = (WebEOCWebServiceClient) context.getBean("webEOCWebServiceClient");

        if (client != null) {
            ArrayOfString list = client.getIncidentList();
            for (String id : list.getStringArray()) {

                System.out.println("abc" + id);
            }
        } else {
            System.err.println("webEOCWebServiceClient not found in context");
        }
    }
}

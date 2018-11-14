package it.cnr.si.cool.jconon.service.helpdesk;

import it.cnr.cool.cmis.service.CMISService;
import it.cnr.cool.mail.MailService;
import it.cnr.cool.mail.model.AttachmentBean;
import it.cnr.cool.mail.model.EmailMessage;
import it.cnr.cool.security.service.UserService;
import it.cnr.cool.security.service.impl.alfresco.CMISUser;
import it.cnr.cool.service.I18nService;
import it.cnr.cool.util.StringUtil;
import it.cnr.cool.web.scripts.exception.ClientMessageException;
import it.cnr.si.cool.jconon.exception.HelpDeskNotConfiguredException;
import it.cnr.si.cool.jconon.model.HelpdeskBean;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.Optional;

import org.apache.chemistry.opencmis.client.bindings.impl.CmisBindingsHelper;
import org.apache.chemistry.opencmis.client.bindings.spi.http.Response;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.impl.UrlBuilder;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Created by cirone on 27/10/2014.
 * Modified by marco.spasiano 25/06/2015
 */
@Service
public class HelpdeskService {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(HelpdeskService.class);

    @Autowired
    private MailService mailService;

    @Autowired
    private CMISService cmisService;

    @Autowired
    private UserService userService;

    @Autowired
    private I18nService i18nService;

    @Value("${helpdesk.catg.url}")
    private String helpdeskCatgURL;
    @Value("${helpdesk.user.url}")
    private String helpdeskUserURL;
    @Value("${helpdesk.ucat.url}")
    private String helpdeskUcatURL;
    @Value("${helpdesk.pest.url}")
    private String helpdeskPestURL;


    @Value("${helpdesk.username}")
    private String userName;
    @Value("${helpdesk.password}")
    private String password;
    @Value("${mail.from.default}")
    private String sender;

    public String sendReopenMessage(HelpdeskBean hdBean) throws MailException, IOException {
        return sendMessage(hdBean, null);
    }

    public String post(
            HelpdeskBean hdBean, MultipartFile allegato,
            CMISUser user) throws IOException, MailException , CmisObjectNotFoundException{

        hdBean.setMatricola("0");

        if (user != null && !user.isGuest()
                && user.getFirstName() != null
                && user.getFirstName().equals(hdBean.getFirstName())
                && user.getLastName() != null
                && user.getLastName().equals(hdBean.getLastName())
                && user.getMatricola() != null) {
            hdBean.setMatricola(String.valueOf(user.getMatricola()));
            hdBean.setConfirmRequested(false);
        }
        // eliminazione caratteri problematici
        hdBean.setSubject(cleanText(hdBean.getSubject()));
        hdBean.setFirstName(cleanText(hdBean.getFirstName()));
        hdBean.setLastName(cleanText(hdBean.getLastName()));
        hdBean.setMessage(cleanText(hdBean.getMessage()));
        hdBean.setEmail(hdBean.getEmail().trim());

        Integer category = Integer.valueOf(hdBean.getCategory());
        try {
            if(getEsperti(category).equals("{}")){
                LOGGER.error("La categoria con id " + category + " (Bando \"" + hdBean.getCall() + "\") NON HA NESSUN ESPERTO!");
            }
            if(category == 1){
                LOGGER.warn("Il Bando \"" + hdBean.getCall() + "\" NON HA NESSUN ID ASSOCIATO ALLA CATEGORIA " + hdBean.getProblemType() + " !");
            }
        } catch(HelpDeskNotConfiguredException _ex) {
        }
        return sendMessage(hdBean, allegato);
    }

    private String sendMessage(HelpdeskBean hdBean, MultipartFile allegato) throws MailException, IOException {
        JSONObject json = new JSONObject();
        if (Optional.ofNullable(hdBean.getId()).isPresent())
            json.put("id", hdBean.getId());
        json.put("titolo", hdBean.getCall() + " - " + hdBean.getSubject());
        json.put("categoria", hdBean.getCategory());
        json.put("categoriaDescrizione", hdBean.getCall() + " - " + hdBean.getProblemType());
        json.put("firstName", hdBean.getFirstName());
        json.put("familyName", hdBean.getLastName());
        json.put("email", hdBean.getEmail());
        json.put("descrizione", hdBean.getMessage());
        json.put("confirmRequested", Optional.ofNullable(hdBean.isConfirmRequested()).map(aBoolean -> {
            if (aBoolean)
                return "y";
            return "n";
        }).orElse("y"));
        UrlBuilder url = new UrlBuilder(helpdeskPestURL);
        PutMethod method = new PutMethod(url.toString());
        try {
            method.setRequestEntity(new StringRequestEntity(json.toString(), "application/json", "UTF-8"));
            HttpClient httpClient = getHttpClient();
            int statusCode = httpClient.executeMethod(method);
            if (statusCode != HttpStatus.CREATED.value() && statusCode != HttpStatus.NO_CONTENT.value()) {
                LOGGER.error("Errore in fase di creazione segnalazione helpdesk dalla URL: {} JSON {}", helpdeskPestURL, json);
                LOGGER.error(method.getResponseBodyAsString());
            } else {
                String id = method.getResponseBodyAsString();
                if (allegato != null && !allegato.isEmpty()) {
                    UrlBuilder urlAllegato = new UrlBuilder(helpdeskPestURL.concat("/").concat(id));
                    PostMethod methodAllegato = new PostMethod(urlAllegato.toString());
                    try {
                        FilePart filePart = new FilePart("allegato", new ByteArrayPartSource(allegato.getName(), allegato.getBytes()));
                        Part[] parts = {filePart};
                        methodAllegato.setRequestEntity(new MultipartRequestEntity(parts, methodAllegato.getParams()));
                        int statusCodeAllegato = httpClient.executeMethod(methodAllegato);
                        if (HttpStatus.NO_CONTENT.value()!=statusCodeAllegato) {
                            LOGGER.error("Errore in fase di creazione allegato helpdesk dalla URL {} id {}", helpdeskPestURL, id);
                            LOGGER.error(methodAllegato.getResponseBodyAsString());
                        }
                    } finally {
                        methodAllegato.releaseConnection();;
                    }
                }
                LOGGER.debug(method.getResponseBodyAsString());
                return id;
            }
        } catch (IOException e) {
            LOGGER.error("Errore in fase di creazione della segnalazione helpdesk - "
                    + e.getMessage() + " dalla URL:" + helpdeskPestURL, e);
        } finally{
            method.releaseConnection();
        }
        return null;
    }

    private String cleanText(String text) {
        if (text == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32)
                continue;
            if (c == 224 || c == 225)
                sb.append('a');
            else if (c == 232 || c == 233)
                sb.append('e');
            else if (c == 236 || c == 237)
                sb.append('i');
            else if (c == 242 || c == 243)
                sb.append('o');
            else if (c == 249 || c == 250)
                sb.append('u');
            else if (c < 127)
                sb.append(c);
        }
        return sb.toString();
    }

    private HttpClient getHttpClient() {
        HttpClient httpClient = new HttpClient();
        Credentials credentials = new UsernamePasswordCredentials(userName, password);
        httpClient.getState().setCredentials(AuthScope.ANY, credentials);
        return httpClient;
    }


    public Integer getCategoriaMaster(String callType) {
        String link = cmisService.getBaseURL().concat("service/cnr/jconon/categorie-helpdesk");
        UrlBuilder url = new UrlBuilder(link);
        Response resp = CmisBindingsHelper.getHttpInvoker(cmisService.getAdminSession()).invokeGET(url, cmisService.getAdminSession());
        int status = resp.getResponseCode();
        if (status == HttpStatus.OK.value()) {
            JSONObject jsonObject = new JSONObject(StringUtil.convertStreamToString(resp.getStream()));
            return jsonObject.getInt(callType);
        }
        return 1;
    }
    public Integer createCategoria(Integer idPadre, String nome, String descrizione) {
        Integer idCategoriaHelpDesk = null;
        // Create an instance of HttpClient.
        JSONObject json = new JSONObject();
        json.put("idPadre", idPadre == null?1:idPadre);
        json.put("nome", nome);
        json.put("descrizione", descrizione);

        UrlBuilder url = new UrlBuilder(helpdeskCatgURL);
        PutMethod method = new PutMethod(url.toString());
        try {
            method.setRequestEntity(new StringRequestEntity(json.toString(), "application/json", "UTF-8"));
            HttpClient httpClient = getHttpClient();
            int statusCode = httpClient.executeMethod(method);
            if (statusCode != HttpStatus.OK.value()) {
                LOGGER.error("Errore in fase di creazione della categoria heldesk dalla URL:" + helpdeskCatgURL);
            } else {
                LOGGER.debug(method.getResponseBodyAsString());
                idCategoriaHelpDesk = Integer.valueOf(method.getResponseBodyAsString());
            }
        } catch (IOException e) {
            LOGGER.error("Errore in fase di creazione della categoria heldesk - "
                    + e.getMessage() + " dalla URL:" + helpdeskCatgURL, e);
        } finally{
            method.releaseConnection();
        }
        return idCategoriaHelpDesk;
    }

    public Object getEsperti(Integer idCategoria) {
        Optional.ofNullable(helpdeskUcatURL).filter(x -> x.length() >0).orElseThrow(() -> new HelpDeskNotConfiguredException());
        UrlBuilder url = new UrlBuilder(helpdeskUcatURL);
        GetMethod method = new GetMethod(url.toString() + "/" + idCategoria);
        try {
            HttpClient httpClient = getHttpClient();
            int statusCode = httpClient.executeMethod(method);
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                LOGGER.error("Errore in fase di recupero delle categorie helpdesk dalla URL:" + helpdeskUcatURL);
            } else if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "{}";
            } else {
                LOGGER.debug(method.getResponseBodyAsString());
                return method.getResponseBodyAsString();
            }
        } catch (IOException e) {
            LOGGER.error("Errore in fase di creazione della categoria heldesk - "
                    + e.getMessage() + " dalla URL:" + helpdeskCatgURL, e);
        } finally{
            method.releaseConnection();
        }
        return "{}";
    }

    public Object manageEsperto(Integer idCategoria, String idEsperto, boolean delete) {
        UrlBuilder url = new UrlBuilder(helpdeskUcatURL);
        HttpMethod method = null;
        if (delete)
            method = new DeleteMethod(url.toString() + "/" + idCategoria + "/" + idEsperto);
        else {
            inserisciEsperto(idEsperto);
            method = new PutMethod(url.toString() + "/" + idCategoria + "/" + idEsperto);
        }
        try {
            HttpClient httpClient = getHttpClient();
            int statusCode = httpClient.executeMethod(method);
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                LOGGER.error("Errore in fase di gestione esperto helpdesk dalla URL:" + helpdeskUcatURL);
            } else {
                LOGGER.debug(method.getResponseBodyAsString());
                return method.getResponseBodyAsString();
            }
        } catch (IOException e) {
            LOGGER.error("Errore in fase di creazione della categoria heldesk - "
                    + e.getMessage() + " dalla URL:" + helpdeskCatgURL, e);
        } finally{
            method.releaseConnection();
        }
        return null;
    }

    private void inserisciEsperto(String idEsperto) {
        CMISUser user = userService.loadUserForConfirm(idEsperto);
        JSONObject json = new JSONObject();
        json.put("firstName", user.getFirstName());
        json.put("familyName", user.getLastName());
        json.put("login", user.getId());
        json.put("email", user.getEmail());
        json.put("telefono", user.getTelephone());
        json.put("struttura", "1");
        json.put("profile", "2");
        json.put("mailStop", "n");
        UrlBuilder url = new UrlBuilder(helpdeskUserURL);
        PutMethod method = new PutMethod(url.toString());
        try {
            method.setRequestEntity(new StringRequestEntity(json.toString(), "application/json", "UTF-8"));
            HttpClient httpClient = getHttpClient();
            int statusCode = httpClient.executeMethod(method);
            if (statusCode != HttpStatus.CREATED.value() && statusCode != HttpStatus.NO_CONTENT.value()) {
                LOGGER.error("Errore in fase di creazione del'utente helpdesk dalla URL:" + helpdeskUserURL);
                LOGGER.error(method.getResponseBodyAsString());
            } else {
                LOGGER.debug(method.getResponseBodyAsString());
            }
        } catch (IOException e) {
            LOGGER.error("Errore in fase di creazione della categoria heldesk - "
                    + e.getMessage() + " dalla URL:" + helpdeskCatgURL, e);
        } finally{
            method.releaseConnection();
        }
    }


    //servono per settare il mockMailService nei test
    public MailService getMailService() {
        return mailService;
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

}